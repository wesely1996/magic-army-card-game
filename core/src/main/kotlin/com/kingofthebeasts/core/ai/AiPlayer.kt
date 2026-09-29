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
    EASY("Easy", "Plays on instinct: picks what looks best right now, and sometimes slips."),
    MEDIUM("Medium", "Thinks 3 moves ahead: its move, your best reply, and its follow-up."),
}

/**
 * The computer opponent.
 *
 * - [Difficulty.EASY] simulates each legal action one step ahead and picks the
 *   best-looking result, with enough noise to make mistakes.
 * - [Difficulty.MEDIUM] runs a 3-ply alpha-beta search (its action, the
 *   opponent's reply, its next action) over the most promising candidates at
 *   each level. It does not peek at the opponent's hand: when predicting
 *   replies it only considers moves, attacks and abilities on the board.
 *
 * Simulations assume nobody interrupts; interrupts are decided separately when
 * a response window actually opens.
 */
class AiPlayer(val difficulty: Difficulty = Difficulty.MEDIUM, seed: Long = 1L) {
    private val rng = Random(seed)

    fun choose(s: GameState): Action {
        val d = GameEngine.decision(s)
        return when (d.kind) {
            DecisionKind.DEPLOY -> chooseDeploy(s, d.player)
            DecisionKind.MAIN -> when (difficulty) {
                Difficulty.EASY -> chooseGreedy(s, d.player, noise = 1.6)
                Difficulty.MEDIUM -> chooseBySearch(s, d.player)
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
        var best: Action = Action.Pass
        var bestScore = Double.NEGATIVE_INFINITY
        for ((a, sim) in expand(s, includeCards = true)) {
            val score = Evaluator.evaluate(sim, p) + rng.nextDouble() * noise
            if (score > bestScore) {
                bestScore = score
                best = a
            }
        }
        return best
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

    // ------------------------------------------------------------- interrupts

    private fun chooseResponse(s: GameState, p: Int): Action {
        val passSim = s.copyForSimulation()
        GameEngine.applyUnchecked(passSim, Action.Pass)
        val passScore = Evaluator.evaluate(passSim, p)
        // Easy players often miss the chance to interrupt.
        if (difficulty == Difficulty.EASY && rng.nextDouble() < 0.5) return Action.Pass
        var best: Action = Action.Pass
        var bestScore = passScore + 0.75
        for (a in GameEngine.legalActions(s, distinctCards = true)) {
            if (a == Action.Pass) continue
            val sim = s.copyForSimulation()
            GameEngine.applyUnchecked(sim, a)
            passAll(sim)
            val score = Evaluator.evaluate(sim, p)
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
        const val SEARCH_PLIES = 3
        const val ROOT_BEAM = 10
        const val INNER_BEAM = 6
    }
}
