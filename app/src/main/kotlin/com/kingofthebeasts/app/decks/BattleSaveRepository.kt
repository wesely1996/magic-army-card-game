package com.kingofthebeasts.app.decks

import android.content.Context
import com.kingofthebeasts.core.game.SavedBattle
import java.io.File

/** Keeps the battle in progress (if any) in the app's private storage so it can be resumed. */
class BattleSaveRepository(context: Context) {
    private val file = File(context.filesDir, "battle.json")

    fun load(): SavedBattle? = runCatching { if (file.exists()) SavedBattle.decode(file.readText()) else null }.getOrNull()

    fun save(battle: SavedBattle) {
        runCatching {
            val tmp = File(file.parentFile, "battle.json.tmp")
            tmp.writeText(battle.encode())
            tmp.renameTo(file)
        }
    }

    fun clear() {
        file.delete()
    }
}
