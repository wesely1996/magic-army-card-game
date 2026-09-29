package com.magicarmy.core.ai

import com.magicarmy.core.game.Action
import com.magicarmy.core.game.Board
import com.magicarmy.core.game.DecisionKind
import com.magicarmy.core.game.GameEngine
import com.magicarmy.core.game.GameState
import com.magicarmy.core.game.Phase
import kotlin.math.abs
import kotlin.random.Random

/**
 * A greedy opponent: it simulates every legal action one step ahead (assuming
 * the other player does not interrupt) and picks the best-scoring result.
 */
class AiPlayer(seed: Long = 1L) {
    private val rng = Random(seed)

    fun choose(s: GameState): Action {
        val d = GameEngine.decision(s)
        return when (d.kind) {
            DecisionKind.DEPLOY -> chooseDeploy(s, d.player)
            DecisionKind.MAIN -> chooseMain(s, d.player)
            DecisionKind.RESPOND -> chooseResponse(s, d.player)
            DecisionKind.NONE -> Action.Pass
        }
    }

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

    private fun chooseMain(s: GameState, p: Int): Action {
        var best: Action = Action.Pass
        var bestScore = Double.NEGATIVE_INFINITY
        for (a in GameEngine.legalActions(s, distinctCards = true)) {
            val sim = s.copyForSimulation()
            GameEngine.applyUnchecked(sim, a)
            passAll(sim)
            val score = Evaluator.evaluate(sim, p) + rng.nextDouble() * 0.3
            if (score > bestScore) {
                bestScore = score
                best = a
            }
        }
        return best
    }

    private fun chooseResponse(s: GameState, p: Int): Action {
        val passSim = s.copyForSimulation()
        GameEngine.applyUnchecked(passSim, Action.Pass)
        val passScore = Evaluator.evaluate(passSim, p)
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

    /** Lets any open response window resolve without further interrupts. */
    private fun passAll(sim: GameState) {
        while (sim.phase == Phase.BATTLE && sim.priority != null) GameEngine.applyUnchecked(sim, Action.Pass)
    }
}
