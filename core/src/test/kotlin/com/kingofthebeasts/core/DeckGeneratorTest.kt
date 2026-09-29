package com.kingofthebeasts.core

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.DeckGenerator
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.model.Race
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DeckGeneratorTest {
    @Test
    fun generatesLegalDecksForEveryRaceCombinationAndKing() {
        val races = Race.entries
        val combos = races.map { listOf(it) } +
            races.flatMapIndexed { i, a -> races.drop(i + 1).map { b -> listOf(a, b) } } +
            races.flatMapIndexed { i, a ->
                races.drop(i + 1).flatMapIndexed { j, b -> races.drop(i + j + 2).map { c -> listOf(a, b, c) } }
            }
        assertEquals(5 + 10 + 10, combos.size)
        val rng = Random(1)
        for (combo in combos) for (king in CardDatabase.all.filter { it.isKing && it.race in combo }) repeat(5) {
            val deck = DeckGenerator.generate(combo, king.id, rng)
            assertEquals(emptyList(), DeckRules.validate(deck), "$combo / ${king.id}")
        }
    }
}
