package com.kingofthebeasts.core

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Target
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.FieldRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Card tiers (stars), copy limits, 40 + King decks and Strategy fields. */
class RulesV4Test {
    @Test
    fun copyLimitsFollowTheStars() {
        for (c in CardDatabase.all) {
            val expected = when {
                c.isKing -> 1
                c.type == CardType.UNIT || c.type == CardType.MAGIC -> 4 - c.stars
                else -> 3
            }
            assertEquals(expected, c.maxCopies, c.id)
        }
        assertEquals(1, CardDatabase.get("w_packlord").maxCopies, "Champions: 1 copy")
        assertEquals(3, CardDatabase.get("w_packlord").unit!!.slots, "Champions take 3 slots")
        assertEquals(2, CardDatabase.get("w_direwolf").maxCopies, "Elites: 2 copies")
        assertEquals(1, CardDatabase.get("w_bite").maxCopies, "3-star spells: 1 copy")
        assertEquals(2, CardDatabase.get("w_scatter").maxCopies, "2-star spells: 2 copies")
        assertEquals(3, CardDatabase.get("w_frenzy").maxCopies, "1-star spells: 3 copies")
    }

    @Test
    fun everyRaceHasAChampion() {
        for (race in com.kingofthebeasts.core.model.Race.entries) {
            assertTrue(CardDatabase.ofRace(race).any { it.unit?.isChampion == true }, race.name)
        }
    }

    @Test
    fun decksAre40CardsPlusTheKingWithAtMost3Strategies() {
        val d = StarterDecks.all[0]
        assertEquals(DeckRules.DECK_SIZE, d.mainSize)
        assertEquals(DeckRules.DECK_SIZE + 1, d.size)
        val strategy = CardDatabase.ofRace(d.races[0]).first { it.type == CardType.STRATEGY }
        val tooMany = d.withCount(strategy.id, 3).withCount("w_hunt", 3).withCount("l_banner", 0)
        assertTrue(DeckRules.validate(tooMany).any { "Strategy" in it } || tooMany.strategyCount <= DeckRules.MAX_STRATEGY)
        val four = d.withCount("w_hunt", 3).withCount("w_moonlit", 1)
        assertTrue(DeckRules.validate(four).any { "Strategy" in it }, "4 Strategy cards are too many")
    }

    @Test
    fun aFieldLastsUntilAnyStrategyReplacesItAndStrategiesAreExhausted() {
        val (s, _) = battle(Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)))
        val drums = s.giveCard(0, "l_banner")
        GameEngine.apply(s, Action.PlayCard(drums.uid, Target.None))
        assertTrue(s.fieldActive(0, FieldRule.WAR_DRUMS))
        assertTrue(drums in s.players[0].exhausted, "Strategy cards are used once")
        repeat(6) { GameEngine.apply(s, Action.Pass) }
        assertTrue(s.fieldActive(0, FieldRule.WAR_DRUMS), "no time limit")
        val swamp = s.giveCard(1, "s_swamp")
        if (s.activePlayer != 1) GameEngine.apply(s, Action.Pass)
        GameEngine.apply(s, Action.PlayCard(swamp.uid, Target.None))
        assertEquals(1, s.fields.size, "one field at a time")
        assertTrue(s.fieldActive(1, FieldRule.SWAMP))
        assertFalse(s.fieldActive(0, FieldRule.WAR_DRUMS), "the opponent's Strategy replaced it")
    }
}
