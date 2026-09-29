package com.magicarmy.core.deck

import com.magicarmy.core.data.CardDatabase
import com.magicarmy.core.model.Race
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class Deck(
    val name: String,
    val races: List<Race>,
    /** card id -> number of copies */
    val cards: Map<String, Int>,
) {
    val size: Int get() = cards.values.sum()

    fun cardIds(): List<String> = cards.flatMap { (id, n) -> List(n) { id } }

    fun withCount(cardId: String, count: Int): Deck =
        copy(cards = if (count <= 0) cards - cardId else cards + (cardId to count))
}

object DeckRules {
    const val DECK_SIZE = 40
    const val MAX_RACES = 3

    fun validate(deck: Deck): List<String> {
        val errors = mutableListOf<String>()
        if (deck.name.isBlank()) errors += "Give the deck a name."
        if (deck.races.isEmpty()) errors += "Pick at least one race."
        if (deck.races.size > MAX_RACES) errors += "A deck can use at most $MAX_RACES races."
        if (deck.size != DECK_SIZE) errors += "The deck must have exactly $DECK_SIZE cards (has ${deck.size})."
        var kings = 0
        for ((id, count) in deck.cards) {
            val def = CardDatabase.find(id)
            if (def == null) {
                errors += "Unknown card: $id"
                continue
            }
            if (def.race !in deck.races) errors += "${def.name} is not from one of the deck's races."
            if (count > def.maxCopies) errors += "At most ${def.maxCopies} copies of ${def.name}."
            if (def.isKing) kings += count
        }
        if (kings != 1) errors += "The deck must contain exactly one King (has $kings)."
        return errors
    }

    fun isValid(deck: Deck) = validate(deck).isEmpty()
}

object DeckCodec {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val listSerializer = ListSerializer(Deck.serializer())

    fun encode(decks: List<Deck>): String = json.encodeToString(listSerializer, decks)
    fun decode(text: String): List<Deck> = if (text.isBlank()) emptyList() else json.decodeFromString(listSerializer, text)
}
