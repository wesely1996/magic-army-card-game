package com.magicarmy.core.ai

import com.magicarmy.core.game.GameEngine
import com.magicarmy.core.game.GameState
import com.magicarmy.core.game.Phase
import com.magicarmy.core.game.UnitState
import com.magicarmy.core.model.CardDef
import com.magicarmy.core.model.Keyword
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
        for (f in s.fields) score += (if (f.owner == me) 1 else -1) * 1.2 * minOf(f.turns, 3)

        if (s.phase == Phase.BATTLE) {
            val mover = s.activePlayer
            score -= danger(s, me) * (if (mover == opp) 1.0 else 0.35)
            score += danger(s, opp) * (if (mover == me) 1.0 else 0.35)
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

    /**
     * How badly [side]'s units can be hurt by the enemy's next single action.
     * Only one attack happens per turn, so the worst threat counts fully and
     * the others only a little.
     */
    private fun danger(s: GameState, side: Int): Double {
        val attackers = s.unitsOf(1 - side).filter { it.stun == 0 }
        if (attackers.isEmpty()) return 0.0
        val zones = attackers.map { a -> a to (GameEngine.reachable(s, a) + a.pos) }
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
