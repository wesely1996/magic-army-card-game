package com.kingofthebeasts.core.ai

import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.UnitState
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.Keyword
import kotlin.math.max

/** Heuristic score of a position from [me]'s point of view. Higher is better. */
object Evaluator {
    private const val WIN = 1_000_000.0

    fun evaluate(s: GameState, me: Int): Double {
        if (s.phase == Phase.GAME_OVER) return when {
            s.isDraw -> 0.0
            s.winner == me -> WIN
            else -> -WIN
        }
        val opp = 1 - me
        var score = 0.0
        for (u in s.units) {
            if (!u.alive) continue
            val v = unitValue(s, u)
            if (u.owner == me) score += v else score -= v
        }
        score += (s.players[me].hand.size - s.players[opp].hand.size) * 1.5
        for (f in s.fields) score += (if (f.owner == me) 1 else -1) * 4.0

        if (s.phase == Phase.BATTLE) {
            val mover = s.activePlayer
            score -= danger(s, me) * (if (mover == opp) 1.0 else 0.35)
            score += danger(s, opp) * (if (mover == me) 1.0 else 0.35)
            score -= siege(s, me) * SIEGE_WEIGHT
            score += siege(s, opp) * SIEGE_WEIGHT
        }

        val oppKing = s.king(opp)
        val myKing = s.king(me)
        for (u in s.unitsOf(me)) if (!u.isKing && oppKing != null) score -= 0.12 * u.pos.distanceTo(oppKing.pos)
        for (u in s.unitsOf(opp)) if (!u.isKing && myKing != null) score += 0.12 * u.pos.distanceTo(myKing.pos)
        return score
    }

    fun unitValue(s: GameState, u: UnitState): Double {
        if (u.isKing) return 60.0 + u.hp * 4.0 + GameEngine.attackOf(s, u)
        var v = GameEngine.attackOf(s, u) * 1.6 + u.hp + GameEngine.moveOf(s, u) * 0.4 +
            (GameEngine.rangeOf(s, u) - 1) * 1.2 + u.keywords.size * 0.8 + u.abilities.size * 1.2 + u.shield * 0.7
        if (u.stun > 0) v -= 1.5
        v -= u.poisonTurns * u.poisonDamage * 0.8
        return max(0.5, v)
    }

    fun cardUnitValue(def: CardDef): Double {
        val st = def.unit ?: return 0.0
        return st.attack * 1.6 + st.health + st.move * 0.4 + (st.range - 1) * 1.2 +
            st.keywords.size * 0.8 + st.abilities.size * 1.2
    }

    /** How much a point of [siege] costs: a King's health is worth 4, counted at half weight. */
    private const val SIEGE_WEIGHT = 2.0
    private const val SIEGE_TURNS = 4

    /**
     * The damage [side]'s King stands to take from enemies already within reach of him, over the turns
     * each will take to kill: an attacker that would need three blows to bring down is three hits on
     * the King. [danger] only sees the next blow, which doesn't change while the King's guard chips
     * at an attacker, so without this the AI would rather grow its army than fight off a siege.
     */
    private fun siege(s: GameState, side: Int): Double {
        val king = s.king(side) ?: return 0.0
        var total = 0.0
        for (e in s.unitsOf(1 - side)) {
            if (e.has(Keyword.STRUCTURE) && !e.has(Keyword.SENTRY)) continue
            val reach = GameEngine.rangeOf(s, e) + if (GameEngine.canAttackAfterMove(e)) GameEngine.moveOf(s, e) else 0
            if (e.pos.distanceTo(king.pos) > reach) continue
            val hit = GameEngine.attackDamage(s, e, king)
            if (hit <= 0) continue
            // The hardest blow the King's side can strike at it now.
            var best = 0
            for (m in s.unitsOf(side)) {
                if (m.has(Keyword.STRUCTURE) || m.stun > 0) continue
                val r = GameEngine.rangeOf(s, m) + if (GameEngine.canAttackAfterMove(m)) GameEngine.moveOf(s, m) else 0
                if (m.pos.distanceTo(e.pos) <= r) best = max(best, GameEngine.attackDamage(s, m, e))
            }
            val turns = if (best <= 0) SIEGE_TURNS else minOf(SIEGE_TURNS, (e.hp + best - 1) / best)
            total += hit * turns
        }
        return total
    }

    /**
     * How badly [side]'s units can be hurt by the enemy's next single action.
     * Only one attack happens per turn, so the worst threat counts fully and
     * the others only a little.
     */
    private fun danger(s: GameState, side: Int): Double {
        // Structures only threaten with Sentry; ranged units can't move and attack in the same turn.
        val attackers = s.unitsOf(1 - side).filter { it.stun == 0 && (!it.has(Keyword.STRUCTURE) || it.has(Keyword.SENTRY)) }
        if (attackers.isEmpty()) return 0.0
        val zones = attackers.map { a ->
            a to (if (GameEngine.canAttackAfterMove(a)) GameEngine.reachable(s, a) + a.pos else listOf(a.pos))
        }
        val penalties = mutableListOf<Double>()
        for (t in s.unitsOf(side)) {
            var worst = 0
            for ((a, squares) in zones) {
                val r = GameEngine.rangeOf(s, a)
                if (squares.none { it.distanceTo(t.pos) <= r }) continue
                var d = GameEngine.attackDamage(s, a, t)
                if (t.has(Keyword.ARMORED)) d -= 1
                d = max(0, d - t.shield)
                if (t.has(Keyword.UNSTOPPABLE)) d = minOf(d, 3)
                worst = max(worst, d)
            }
            if (worst == 0) continue
            penalties += when {
                worst >= t.hp -> if (t.isKing) 5000.0 else unitValue(s, t) * 0.8 + worst
                t.isKing -> worst * 4.0 * 0.8
                else -> worst * 0.8
            }
        }
        if (penalties.isEmpty()) return 0.0
        val top = penalties.max()
        return top + (penalties.sum() - top) * 0.25
    }
}
