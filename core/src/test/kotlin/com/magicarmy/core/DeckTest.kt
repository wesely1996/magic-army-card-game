package com.magicarmy.core

import com.magicarmy.core.data.CardDatabase
import com.magicarmy.core.data.StarterDecks
import com.magicarmy.core.deck.Deck
import com.magicarmy.core.deck.DeckCodec
import com.magicarmy.core.deck.DeckRules
import com.magicarmy.core.model.CardType
import com.magicarmy.core.model.Race
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckTest {
    @Test
    fun cardIdsAreUnique() {
        assertEquals(CardDatabase.all.size, CardDatabase.all.map { it.id }.toSet().size)
    }

    @Test
    fun everyRaceHasTwoKingsAndEnoughCardsForAMonoRaceDeck() {
        for (race in Race.entries) {
            val cards = CardDatabase.ofRace(race)
            assertEquals(2, cards.count { it.isKing }, "$race kings")
            assertTrue(1 + cards.filter { !it.isKing }.sumOf { it.maxCopies } >= DeckRules.DECK_SIZE, "$race pool size")
            for (type in CardType.entries) assertTrue(cards.any { it.type == type }, "$race has $type")
        }
    }

    @Test
    fun starterDecksAreValid() {
        for (d in StarterDecks.all) assertEquals(emptyList(), DeckRules.validate(d), d.name)
    }

    @Test
    fun validationCatchesBrokenDecks() {
        val base = StarterDecks.all[0]
        assertTrue(DeckRules.validate(base.withCount("w_pup", 4)).any { "copies" in it })
        assertTrue(DeckRules.validate(base.withCount("l_king_queen", 1)).any { "King" in it })
        assertTrue(DeckRules.validate(base.withCount("b_cub", 1)).any { "not from" in it })
        assertTrue(DeckRules.validate(base.copy(races = Race.entries.take(4))).any { "at most 3" in it })
        assertTrue(DeckRules.validate(base.withCount("w_pup", 2)).any { "exactly 40" in it })
        assertTrue(DeckRules.validate(Deck("", emptyList(), emptyMap())).isNotEmpty())
    }

    @Test
    fun codecRoundTrips() {
        val decks = StarterDecks.all
        assertEquals(decks, DeckCodec.decode(DeckCodec.encode(decks)))
        assertEquals(emptyList(), DeckCodec.decode(""))
    }
}
