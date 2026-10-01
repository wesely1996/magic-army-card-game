package com.kingofthebeasts.app.decks

import android.content.Context
import com.kingofthebeasts.core.net.OnlineSave
import java.io.File

/** Keeps the online battle in progress (if any), so it can be rejoined after a drop or a restart. */
class OnlineSaveRepository(context: Context) {
    private val file = File(context.filesDir, "online.json")

    fun load(): OnlineSave? = runCatching { if (file.exists()) OnlineSave.decode(file.readText()) else null }.getOrNull()

    fun save(battle: OnlineSave?) {
        if (battle == null) {
            file.delete()
            return
        }
        runCatching {
            val tmp = File(file.parentFile, "online.json.tmp")
            tmp.writeText(battle.encode())
            tmp.renameTo(file)
        }
    }
}
