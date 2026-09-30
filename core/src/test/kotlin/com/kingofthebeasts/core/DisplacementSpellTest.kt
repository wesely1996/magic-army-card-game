package com.kingofthebeasts.core

import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Each race's displacement or swapping spell. */
class DisplacementSpellTest {
    private fun kings() = arrayOf(Triple("w_king_alpha", 0, p(0, 0)), Triple("b_king_warden", 1, p(7, 7)))

    @Test
    fun mightyShovePushesAwayFromYourNearestUnitAndHurtsOnImpact() {
        val (s, u) = battle(Triple("b_grizzly", 0, p(3, 2)), Triple("s_cobra", 1, p(3, 3)), Triple("w_scout", 1, p(3, 5)), *kings())
        val shove = s.giveCard(0, "b_shove")
        val hp = u[1].hp
        GameEngine.apply(s, Action.PlayCard(shove.uid, Target.Unit(u[1].id)))
        assertEquals(p(3, 4), u[1].pos, "pushed away from the Grizzly until the Scout stopped it")
        assertEquals(hp - 2, u[1].hp, "the collision hurts")
    }

    @Test
    fun mightyShoveWithAClearPathDealsNoDamage() {
        val (s, u) = battle(Triple("b_grizzly", 0, p(3, 2)), Triple("s_cobra", 1, p(3, 3)), *kings())
        val shove = s.giveCard(0, "b_shove")
        GameEngine.apply(s, Action.PlayCard(shove.uid, Target.Unit(u[1].id)))
        assertEquals(p(3, 5), u[1].pos)
        assertEquals(u[1].maxHp, u[1].hp)
    }

    @Test
    fun galeBlowsAnEnemyBackTowardItsOwnSide() {
        val (s, u) = battle(Triple("s_cobra", 1, p(2, 2)), *kings())
        val gale = s.giveCard(0, "h_gale")
        val hp = u[0].hp
        GameEngine.apply(s, Action.PlayCard(gale.uid, Target.Unit(u[0].id)))
        assertEquals(p(2, 5), u[0].pos)
        assertEquals(hp, u[0].hp, "Gale Force deals no damage")
    }

    @Test
    fun galeCantTargetAUnitAlreadyAgainstItsOwnEdge() {
        val (s, u) = battle(Triple("s_cobra", 1, p(2, 7)), *kings())
        val gale = s.giveCard(0, "h_gale")
        assertFalse(GameEngine.legalActions(s).any { it == Action.PlayCard(gale.uid, Target.Unit(u[0].id)) })
    }

    @Test
    fun rallyToTheKingBringsAnAllyNextToYourKing() {
        val (s, u) = battle(Triple("l_guard", 0, p(5, 4)), Triple("l_king_pride", 0, p(1, 0)), Triple("b_king_warden", 1, p(7, 7)))
        val rally = s.giveCard(0, "l_rally")
        assertFalse(GameEngine.legalActions(s).any { it == Action.PlayCard(rally.uid, Target.Unit(u[1].id)) }, "not on the King itself")
        GameEngine.apply(s, Action.PlayCard(rally.uid, Target.Unit(u[0].id)))
        assertEquals(p(2, 1), u[0].pos, "the free square next to the King nearest the Guard")
        assertEquals(p(1, 0), u[1].pos, "the King never moves")
    }

    @Test
    fun kingsAreImmovable() {
        val (s, u) = battle(Triple("b_grizzly", 0, p(3, 2)), Triple("w_king_alpha", 0, p(0, 0)), Triple("b_king_warden", 1, p(3, 3)))
        val shove = s.giveCard(0, "b_shove")
        val gale = s.giveCard(0, "h_gale")
        val mirage = s.giveCard(0, "s_mirage")
        val actions = GameEngine.legalActions(s)
        for (c in listOf(shove, gale, mirage)) {
            assertFalse(actions.any { it == Action.PlayCard(c.uid, Target.Unit(u[2].id)) }, "${c.def.name} can't touch a King")
        }
    }

    @Test
    fun packRelayTradesAnAllyForARandomUnitFromYourDeck() {
        val (s, u) = battle(Triple("w_direwolf", 0, p(4, 4)), *kings())
        val relay = s.giveCard(0, "w_relay")
        val deckSize = s.players[0].deck.size
        GameEngine.apply(s, Action.PlayCard(relay.uid, Target.Unit(u[0].id)))
        assertFalse(u[0] in s.units)
        val fresh = s.unitAt(p(4, 4))!!
        assertEquals(0, fresh.owner)
        assertNotEquals(u[0].id, fresh.id)
        assertTrue(u[0].card in s.players[0].deck, "the Dire Wolf went back into the deck")
        assertEquals(deckSize + 1, s.players[0].deck.size, "one unit out, one in, and Pack Relay itself is shuffled back")
    }

    @Test
    fun mirageReplacesAnEnemyWithARandomUnitFromItsOwnersDeck() {
        val (s, u) = battle(Triple("b_grizzly", 1, p(4, 4)), *kings())
        val mirage = s.giveCard(0, "s_mirage")
        assertFalse(GameEngine.legalActions(s).any { it == Action.PlayCard(mirage.uid, Target.Unit(u[2].id)) }, "Kings can't be replaced")
        GameEngine.apply(s, Action.PlayCard(mirage.uid, Target.Unit(u[0].id)))
        val fresh = s.unitAt(p(4, 4))!!
        assertEquals(1, fresh.owner)
        assertTrue(u[0].card in s.players[1].deck)
        assertFalse(fresh.def.isKing)
    }
}
