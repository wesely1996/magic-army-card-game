package com.kingofthebeasts.core

import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Target
import com.kingofthebeasts.core.model.FieldRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Strategy fields added in 0.11. */
class FieldRulesV11Test {
    private fun field(cardId: String) = battle(
        Triple("l_king_pride", 0, p(3, 0)), Triple("v_king_rat", 1, p(4, 7)),
        Triple("l_cub", 0, p(3, 1)), Triple("l_cub", 0, p(6, 1)), Triple("h_owl", 1, p(4, 5)),
    ).also { (s, _) ->
        val c = s.giveCard(0, cardId)
        GameEngine.apply(s, Action.PlayCard(c.uid, Target.None))
        if (s.priority != null) GameEngine.apply(s, Action.Pass)
    }

    @Test
    fun royalDecreeArmsTheKingAndGuardsHisSide() {
        val (s, u) = field("l_decree")
        assertTrue(s.fieldActive(0, FieldRule.ROYAL_DECREE))
        assertEquals(u[0].attack + 2 + 0, GameEngine.attackOf(s, u[0]) - (if (s.unitsOf(0).any { it.has(com.kingofthebeasts.core.model.Keyword.COMMANDER) && it.id != u[0].id }) 1 else 0))
        val before = u[2].hp
        GameEngine.dealDamage(s, u[2], 3) // next to the King: 1 less
        assertEquals(before - 2, u[2].hp)
        val far = u[3].hp
        GameEngine.dealDamage(s, u[3], 3)
        assertEquals(far - 3, u[3].hp)
    }

    @Test
    fun prideFormationRewardsStandingTogether() {
        val (s, u) = field("l_formation")
        // The cub next to the King gets +1 (plus the King's Commander aura); the lone cub only the aura.
        assertEquals(GameEngine.attackOf(s, u[3]) + 1, GameEngine.attackOf(s, u[2]))
    }

    @Test
    fun hypnoticHazeShortensEnemyRange() {
        val (s, u) = field("s_haze")
        val archer = u[4]
        assertEquals(maxOf(1, archer.range - 1), GameEngine.rangeOf(s, archer))
    }
}
