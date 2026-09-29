package com.magicarmy.app.decks

import android.content.Context
import com.magicarmy.core.deck.Deck
import com.magicarmy.core.deck.DeckCodec
import java.io.File

/** Saves the player's decks as JSON in the app's private storage. */
class DeckRepository(context: Context) {
    private val file = File(context.filesDir, "decks.json")

    fun load(): List<Deck> = runCatching { if (file.exists()) DeckCodec.decode(file.readText()) else emptyList() }
        .getOrDefault(emptyList())

    fun save(decks: List<Deck>) {
        val tmp = File(file.parentFile, "decks.json.tmp")
        tmp.writeText(DeckCodec.encode(decks))
        tmp.renameTo(file)
    }
}
