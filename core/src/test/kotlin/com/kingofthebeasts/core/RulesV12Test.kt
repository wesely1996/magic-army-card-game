package com.kingofthebeasts.core

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Target
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.FieldRule
import com.kingofthebeasts.core.model.Race
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rules added in 0.12: move and attack, Quick spells, structures, evolution, field cleansing. */
class RulesV12Test {
    private fun resolve(s: GameState) {
        while (s.priority != null) GameEngine.apply(s, Action.Pass)
    }

    @Test
    fun championsMayAttackAfterMoving() {
        val (s, u) = battle(
            Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(4, 6)),
            Triple("w_packlord", 0, p(3, 2)), Triple("l_cub", 1, p(3, 5)),
        )
        GameEngine.apply(s, Action.Move(u[2].id, p(3, 4)))
        resolve(s)
        assertEquals(0, s.activePlayer, "still your turn")
        assertEquals(u[2].id, s.followUp)
        val legal = GameEngine.legalActions(s)
        assertTrue(Action.Attack(u[2].id, u[3].id) in legal)
        assertTrue(legal.none { it is Action.Move }, "no second move")
        GameEngine.apply(s, Action.Attack(u[2].id, u[3].id))
        resolve(s)
        assertEquals(1, s.activePlayer, "the attack ends the turn")
        assertNull(s.followUp)
    }

    @Test
    fun ordinaryUnitsMoveOrAttack() {
        val (s, u) = battle(
            Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)),
            Triple("w_hunter", 0, p(3, 2)), Triple("l_cub", 1, p(3, 5)),
        )
        GameEngine.apply(s, Action.Move(u[2].id, p(3, 4)))
        resolve(s)
        assertEquals(1, s.activePlayer, "an ordinary unit's move ends the turn")
    }

    @Test
    fun rangedUnitsCannotMoveAndAttack() {
        val (s, u) = battle(
            Triple("h_king_sky", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)),
            Triple("h_owl", 0, p(3, 1)), Triple("l_cub", 1, p(3, 5)),
        )
        GameEngine.apply(s, Action.Move(u[2].id, p(3, 2)))
        resolve(s)
        assertEquals(1, s.activePlayer, "moving ends a ranged unit's turn")
    }

    @Test
    fun quickSpellsDontUseTheTurn() {
        val (s, u) = battle(Triple("l_king_pride", 0, p(0, 0)), Triple("w_king_alpha", 1, p(7, 7)), Triple("l_cub", 0, p(3, 1)))
        val cry = s.giveCard(0, "l_battlecry")
        GameEngine.apply(s, Action.PlayCard(cry.uid, Target.Unit(u[2].id)))
        resolve(s)
        assertEquals(0, s.activePlayer)
        assertEquals(DecisionKind.MAIN, GameEngine.decision(s).kind)
        val charge = s.giveCard(0, "l_charge")
        GameEngine.apply(s, Action.PlayCard(charge.uid, Target.Unit(u[2].id)))
        resolve(s)
        assertEquals(1, s.activePlayer, "a normal spell ends the turn")
    }

    @Test
    fun tauntForcesAdjacentEnemiesToStrikeIt() {
        val (s, u) = battle(
            Triple("b_king_elder", 0, p(0, 0)), Triple("w_king_alpha", 1, p(7, 7)),
            Triple("b_cairn", 0, p(3, 3)), Triple("b_cub", 0, p(4, 4)), Triple("w_hunter", 1, p(3, 4)),
        )
        s.activePlayer = 1
        val targets = GameEngine.attackTargets(s, u[4]).map { it.id }
        assertEquals(listOf(u[2].id), targets, "only the cairn")
        assertTrue(GameEngine.legalActions(s).none { it is Action.Move && it.unitId == u[2].id }, "structures can't move")
    }

    @Test
    fun sentriesFireAndTotemsHealAtTheStartOfTheTurn() {
        val (s, u) = battle(
            Triple("h_king_sky", 0, p(0, 0)), Triple("w_king_alpha", 1, p(7, 7)),
            Triple("h_tower", 0, p(3, 3)), Triple("b_healtotem", 0, p(1, 1)), Triple("h_falcon", 0, p(1, 2)),
            Triple("w_pup", 1, p(3, 6)),
        )
        u[4].hp -= 1
        val pupHp = u[5].hp
        GameEngine.apply(s, Action.Pass) // your turn ends
        GameEngine.apply(s, Action.Pass) // the rival's ends; your turn starts
        assertTrue(u[5].hp < pupHp || !u[5].alive, "the tower shot the pup")
        assertEquals(u[4].maxHp, u[4].hp, "the totem healed the falcon")
    }

    @Test
    fun unitsEvolveBySurvivingAndByDefeatingEnemies() {
        val (s, u) = battle(
            Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)),
            Triple("w_whelp", 0, p(3, 1)), Triple("l_cub", 1, p(3, 3)),
        )
        val whelp = u[2]
        repeat(4) { GameEngine.apply(s, Action.Pass) } // two of your turns go by
        assertEquals("w_whelp2", whelp.def.id, "Wolf Whelp grew into a Young Wolf")
        assertEquals("Young Wolf", whelp.name)
        u[3].hp = 1
        u[3].pos = p(whelp.pos.x, whelp.pos.y + 1)
        GameEngine.apply(s, Action.Attack(whelp.id, u[3].id))
        resolve(s)
        assertEquals("w_whelp3", whelp.def.id, "a kill makes it a Pack Leader")
        assertEquals(whelp.maxHp, whelp.hp, "evolving heals fully")
        assertEquals(1, GameEngine.slotsOf(whelp.card.def), "still one slot")
    }

    @Test
    fun clearingTheFieldNeedsAField() {
        val (s, _) = battle(Triple("l_king_pride", 0, p(0, 0)), Triple("w_king_alpha", 1, p(7, 7)))
        val clear = s.giveCard(0, "l_twilight")
        assertTrue(GameEngine.legalActions(s).none { it is Action.PlayCard && it.cardUid == clear.uid }, "nothing to clear")
        s.fields += com.kingofthebeasts.core.game.FieldEffect(1, FieldRule.BLITZ, "w_hunt")
        GameEngine.apply(s, Action.PlayCard(clear.uid, Target.None))
        resolve(s)
        assertTrue(s.fields.isEmpty())
    }

    @Test
    fun everyRaceGotItsNewCards() {
        for (race in Race.entries) {
            val cards = CardDatabase.ofRace(race)
            assertTrue(cards.count { it.type == CardType.MAGIC && it.effects.any { e -> e is com.kingofthebeasts.core.model.EffectOp.Draw && e.count == 2 } } >= 1, "$race draw 2")
            assertTrue(cards.any { it.effects.contains(com.kingofthebeasts.core.model.EffectOp.ClearField) }, "$race field cleanse")
            assertTrue(cards.any { it.swift }, "$race quick spell")
            assertTrue(cards.count { it.collectible && it.unit?.evolve != null } >= 2, "$race evolving units")
            assertTrue(cards.any { it.unit?.isStructure == true }, "$race structure")
        }
        assertEquals(10, GameEngine.HAND_LIMIT)
        val form = CardDatabase.get("w_whelp2")
        assertFalse(form.collectible)
        val deck = com.kingofthebeasts.core.data.StarterDecks.all[0]
        assertTrue(DeckRules.validate(deck.withCount("w_whelp2", 1)).any { "evolved" in it })
    }
}
