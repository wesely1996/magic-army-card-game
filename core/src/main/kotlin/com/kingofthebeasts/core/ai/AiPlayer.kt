package com.kingofthebeasts.core.ai

import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.Board
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Phase
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

@Serializable
enum class Difficulty(val displayName: String, val description: String) {
    // The names are stored in saved battles; only the display names change.
    EASY("Beginner", "Plays on instinct: picks what looks best right now, and sometimes slips."),
    MEDIUM("Pro", "Thinks 2 moves ahead: its move and your best reply."),
    HARD("Master", "Thinks as far ahead as time allows, weighs every possible answer, and seldom slips."),
}

/**
 * The computer opponent.
 *
 * - [Difficulty.EASY] simulates each legal action one step ahead and ranks the
 *   results; it takes the best only half the time and otherwise one of the
 *   next four, so it makes beginner's mistakes.
 * - [Difficulty.MEDIUM] runs a 2-ply search (its 16 most promising actions,
 *   each against the opponent's best reply) and looks one action past an interrupt chain
 *   before deciding whether to answer. It does not peek at the opponent's hand: when predicting
 *   replies it only considers moves, attacks and abilities on the board.
 * - [Difficulty.HARD] searches the same way but deeper and wider, deepening
 *   one ply at a time until its [thinkMs] budget runs out (so it adapts to the
 *   phone's speed), reusing each pass's ranking to search the best moves first.
 *   It also looks ahead before deciding whether to interrupt.
 *
 * Simulations assume nobody interrupts; interrupts are decided separately when
 * a response window actually opens.
 */
class AiPlayer(
    val difficulty: Difficulty = Difficulty.MEDIUM,
    seed: Long = 1L,
    /** Randomness added to Easy's choices; lower is steadier (the balance simulator uses a low value). */
    private val greedyNoise: Double = 1.6,
    /** Chance that Easy ignores a chance to interrupt. */
    private val skipInterruptChance: Double = 0.5,
    /** Hard's thinking time per decision, in milliseconds. */
    private val thinkMs: Long = 1500,
    /**
     * Easy picks among this many of its best-looking actions instead of always the best
     * (see [EASY_PICK_WEIGHTS]); the balance simulator sets 1 for a steady player.
     */
    private val easyTopChoices: Int = 5,
) {
    private val rng = Random(seed)

    fun choose(s: GameState): Action {
        val d = GameEngine.decision(s)
        return when (d.kind) {
            DecisionKind.DEPLOY -> chooseDeploy(s, d.player)
            DecisionKind.MAIN -> when (difficulty) {
                Difficulty.EASY -> chooseGreedy(s, d.player, noise = greedyNoise)
                Difficulty.MEDIUM -> chooseBySearch(s, d.player)
                Difficulty.HARD -> chooseByDeepening(s, d.player)
            }
            DecisionKind.RESPOND -> chooseResponse(s, d.player)
            DecisionKind.NONE -> Action.Pass
        }
    }

    // ---------------------------------------------------------------- deploy

    private fun chooseDeploy(s: GameState, p: Int): Action {
        val ps = s.players[p]
        val cards = GameEngine.deployableCards(s, p)
        if (cards.isEmpty()) return Action.EndDeploy
        val card = if (ps.deployed == 0) cards.first()
        else cards.maxBy { Evaluator.cardUnitValue(it.def) + rng.nextDouble() * 2.5 }
        val back = if (p == 0) 0 else Board.SIZE - 1
        val mid = if (p == 0) 1 else Board.SIZE - 2
        val front = if (p == 0) Board.DEPLOY_ROWS - 1 else Board.SIZE - Board.DEPLOY_ROWS
        val st = card.def.unit!!
        val rows = when {
            st.isKing -> listOf(back, mid, front)
            st.range >= 2 -> listOf(mid, back, front)
            else -> listOf(front, mid, back)
        }
        val free = GameEngine.deployTiles(s, p).toSet()
        for (row in rows) {
            val pick = free.filter { it.y == row }
                .minByOrNull { abs(it.x - 3.5) + rng.nextDouble() * (if (st.isKing) 0.5 else 3.0) }
            if (pick != null) return Action.Deploy(card.uid, pick)
        }
        return Action.EndDeploy
    }

    // ------------------------------------------------------------------ easy

    private fun chooseGreedy(s: GameState, p: Int, noise: Double): Action {
        val ranked = expand(s, includeCards = true)
            .map { (a, sim) -> a to settled(sim, p) + rng.nextDouble() * noise }
            .sortedByDescending { it.second }
        if (ranked.isEmpty()) return Action.Pass
        // A winning move is never passed up, even by a beginner.
        if (ranked.first().second >= WIN_SCORE) return ranked.first().first
        val n = minOf(easyTopChoices, ranked.size, EASY_PICK_WEIGHTS.size)
        if (n <= 1) return ranked.first().first
        val weights = EASY_PICK_WEIGHTS.take(n)
        var roll = rng.nextDouble() * weights.sum()
        for (i in 0 until n) {
            roll -= weights[i]
            if (roll <= 0) return ranked[i].first
        }
        return ranked.first().first
    }

    /**
     * The score of [sim] for [p], counting the attack a melee unit may still make after moving
     * (otherwise a one-step look-ahead never sees the point of stepping up to an enemy).
     */
    private fun settled(sim: GameState, p: Int): Double {
        val base = Evaluator.evaluate(sim, p)
        if (sim.phase != Phase.BATTLE || sim.followUp == null || GameEngine.decision(sim).player != p) return base
        val follow = expand(sim, includeCards = false).filter { it.first is Action.Attack }
        return maxOf(base, follow.maxOfOrNull { Evaluator.evaluate(it.second, p) } ?: base)
    }

    // ---------------------------------------------------------------- medium

    private fun chooseBySearch(s: GameState, me: Int): Action {
        val children = expand(s, includeCards = true)
            .map { (a, c) -> Triple(a, c, Evaluator.evaluate(c, me)) }
            .sortedByDescending { it.third }
        if (children.isEmpty()) return Action.Pass
        var best = children.first().first
        var alpha = Double.NEGATIVE_INFINITY
        for ((a, c, _) in children.take(ROOT_BEAM)) {
            val v = value(c, me, SEARCH_PLIES - 1, alpha, Double.POSITIVE_INFINITY) + rng.nextDouble() * 0.2
            if (v > alpha) {
                alpha = v
                best = a
            }
        }
        return best
    }

    /** Alpha-beta value of [s] for [me], looking [plies] more actions ahead. */
    private fun value(s: GameState, me: Int, plies: Int, alphaIn: Double, betaIn: Double): Double {
        if (plies == 0 || s.phase != Phase.BATTLE) return Evaluator.evaluate(s, me)
        val maximizing = GameEngine.decision(s).player == me
        val kids = expand(s, includeCards = maximizing).map { (_, c) -> c to Evaluator.evaluate(c, me) }
        if (kids.isEmpty()) return Evaluator.evaluate(s, me)
        if (plies == 1) return if (maximizing) kids.maxOf { it.second } else kids.minOf { it.second }

        val ordered = (if (maximizing) kids.sortedByDescending { it.second } else kids.sortedBy { it.second }).take(INNER_BEAM)
        var alpha = alphaIn
        var beta = betaIn
        if (maximizing) {
            var v = Double.NEGATIVE_INFINITY
            for ((c, _) in ordered) {
                v = maxOf(v, value(c, me, plies - 1, alpha, beta))
                alpha = maxOf(alpha, v)
                if (alpha >= beta) break
            }
            return v
        } else {
            var v = Double.POSITIVE_INFINITY
            for ((c, _) in ordered) {
                v = minOf(v, value(c, me, plies - 1, alpha, beta))
                beta = minOf(beta, v)
                if (alpha >= beta) break
            }
            return v
        }
    }

    // ------------------------------------------------------------------ hard

    private class OutOfTime : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun chooseByDeepening(s: GameState, me: Int): Action {
        val deadline = System.nanoTime() + thinkMs * 1_000_000
        var order = expand(s, includeCards = true)
            .map { (a, c) -> Triple(a, c, Evaluator.evaluate(c, me)) }
            .sortedByDescending { it.third }
        if (order.isEmpty()) return Action.Pass
        // A winning move needs no thought.
        order.firstOrNull { it.second.phase == Phase.GAME_OVER && it.second.winner == me }?.let { return it.first }
        var best = order.first().first
        for (depth in 2..HARD_MAX_PLIES) {
            val scored = mutableListOf<Triple<Action, GameState, Double>>()
            var alpha = Double.NEGATIVE_INFINITY
            var passBest: Action? = null
            val finished = try {
                for ((a, c, _) in order.take(HARD_ROOT_BEAM)) {
                    val v = deepValue(c, me, depth - 1, alpha, Double.POSITIVE_INFINITY, deadline)
                    scored += Triple(a, c, v)
                    if (v > alpha) {
                        alpha = v
                        passBest = a
                    }
                }
                true
            } catch (_: OutOfTime) {
                false
            }
            // The previous best is searched first, so even a cut-short pass can only improve on it.
            passBest?.let { best = it }
            if (!finished) break
            order = scored.sortedByDescending { it.third } + order.drop(HARD_ROOT_BEAM)
        }
        return best
    }

    /** Like [value], but wider, with a deadline. */
    private fun deepValue(s: GameState, me: Int, plies: Int, alphaIn: Double, betaIn: Double, deadline: Long): Double {
        if (System.nanoTime() > deadline) throw OutOfTime()
        if (plies == 0 || s.phase != Phase.BATTLE) return Evaluator.evaluate(s, me)
        val maximizing = GameEngine.decision(s).player == me
        val kids = expand(s, includeCards = maximizing).map { (_, c) -> c to Evaluator.evaluate(c, me) }
        if (kids.isEmpty()) return Evaluator.evaluate(s, me)
        if (plies == 1) return if (maximizing) kids.maxOf { it.second } else kids.minOf { it.second }
        val beam = if (plies >= 3) HARD_WIDE_BEAM else HARD_INNER_BEAM
        val ordered = (if (maximizing) kids.sortedByDescending { it.second } else kids.sortedBy { it.second }).take(beam)
        var alpha = alphaIn
        var beta = betaIn
        var v = if (maximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        for ((c, _) in ordered) {
            val w = deepValue(c, me, plies - 1, alpha, beta, deadline)
            if (maximizing) {
                v = maxOf(v, w)
                alpha = maxOf(alpha, v)
            } else {
                v = minOf(v, w)
                beta = minOf(beta, v)
            }
            if (alpha >= beta) break
        }
        return v
    }

    // ------------------------------------------------------------- interrupts

    private fun chooseResponse(s: GameState, p: Int): Action {
        val passSim = s.copyForSimulation()
        GameEngine.applyUnchecked(passSim, Action.Pass)
        val passScore = Evaluator.evaluate(passSim, p)
        // Easy players often miss the chance to interrupt.
        if (difficulty == Difficulty.EASY && rng.nextDouble() < skipInterruptChance) return Action.Pass
        // Pro looks one action past the chain before judging an answer (and saving the card), Master two.
        val deadline = System.nanoTime() + thinkMs * 1_000_000 / 2
        val lookAhead = when (difficulty) {
            Difficulty.EASY -> 0
            Difficulty.MEDIUM -> 1
            Difficulty.HARD -> 2
        }
        fun judge(sim: GameState): Double =
            if (lookAhead == 0) Evaluator.evaluate(sim, p)
            else try {
                deepValue(sim, p, lookAhead, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, deadline)
            } catch (_: OutOfTime) {
                Evaluator.evaluate(sim, p)
            }
        var best: Action = Action.Pass
        var bestScore = (if (lookAhead > 0) judge(passSim.also { passAll(it) }) else passScore) + 0.75
        for (a in GameEngine.legalActions(s, distinctCards = true)) {
            if (a == Action.Pass) continue
            val sim = s.copyForSimulation()
            GameEngine.applyUnchecked(sim, a)
            passAll(sim)
            val score = judge(sim)
            if (score > bestScore) {
                bestScore = score
                best = a
            }
        }
        return best
    }

    // ---------------------------------------------------------------- helpers

    /** Every legal action for whoever decides in [s], each with the resulting position. */
    private fun expand(s: GameState, includeCards: Boolean): List<Pair<Action, GameState>> =
        GameEngine.legalActions(s, distinctCards = true)
            .filter { includeCards || it !is Action.PlayCard }
            .map { a ->
                val sim = s.copyForSimulation()
                GameEngine.applyUnchecked(sim, a)
                passAll(sim)
                a to sim
            }

    /** Lets any open response window resolve without further interrupts. */
    private fun passAll(sim: GameState) {
        while (sim.phase == Phase.BATTLE && sim.priority != null) GameEngine.applyUnchecked(sim, Action.Pass)
    }

    private companion object {
        const val SEARCH_PLIES = 2
        const val ROOT_BEAM = 16
        const val INNER_BEAM = 6
        /** How likely Easy is to take its best, 2nd, 3rd, 4th and 5th best-looking action. */
        val EASY_PICK_WEIGHTS = listOf(0.50, 0.20, 0.13, 0.10, 0.07)
        const val WIN_SCORE = 900_000.0
        const val HARD_MAX_PLIES = 7
        const val HARD_ROOT_BEAM = 14
        const val HARD_WIDE_BEAM = 8
        const val HARD_INNER_BEAM = 6
    }
}
