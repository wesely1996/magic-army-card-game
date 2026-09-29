package com.kingofthebeasts.core.deck

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.Race
import kotlin.random.Random

/**
 * Builds a legal, reasonably shaped 40-card deck for any race combination and
 * King. Used by the balance simulator; also handy for "random deck" features.
 */
object DeckGenerator {
    /** Target number of non-King cards per type (39 in total). */
    private val quota = linkedMapOf(
        CardType.UNIT to 17,
        CardType.MAGIC to 11,
        CardType.EQUIPMENT to 7,
        CardType.STRATEGY to 4,
    )

    fun generate(races: List<Race>, kingId: String, rng: Random, name: String = "Generated"): Deck {
        val king = CardDatabase.get(kingId)
        require(king.isKing && king.race in races) { "$kingId is not a King of $races" }
        val counts = linkedMapOf(kingId to 1)
        val pool = CardDatabase.all.filter { it.race in races && !it.isKing }

        fun add(cards: List<CardDef>, target: Int) {
            var added = 0
            // A few passes so that most cards end up with 2-3 copies instead of 1 of everything.
            for (pass in 0 until 3) {
                for (c in cards.shuffled(rng)) {
                    if (added >= target || counts.values.sum() >= DeckRules.DECK_SIZE) return
                    val have = counts[c.id] ?: 0
                    if (have >= c.maxCopies) continue
                    val take = minOf(if (pass == 0) 1 + rng.nextInt(2) else 1, c.maxCopies - have, target - added)
                    counts[c.id] = have + take
                    added += take
                }
            }
        }

        for ((type, n) in quota) add(pool.filter { it.type == type }, n)
        // Small pools (mono-race decks) can't always fill a type's quota; top up from anything left.
        add(pool, DeckRules.DECK_SIZE - counts.values.sum())
        val deck = Deck(name, races, counts)
        check(DeckRules.isValid(deck)) { "Generated an invalid deck: ${DeckRules.validate(deck)}" }
        return deck
    }
}
