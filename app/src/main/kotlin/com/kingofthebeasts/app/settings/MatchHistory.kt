package com.kingofthebeasts.app.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingofthebeasts.core.profile.MatchRecord
import com.kingofthebeasts.core.profile.PlayerStats
import java.io.File

/** Every finished battle (against the AI or a friend), kept on this phone for the profile. */
object MatchHistory {
    /** Older battles beyond this are dropped. */
    const val MAX = 1000

    private var file: File? = null

    /** Oldest first. */
    var records by mutableStateOf<List<MatchRecord>>(emptyList())
        private set

    val stats: PlayerStats get() = PlayerStats.of(records)

    fun load(context: Context) {
        val f = File(context.filesDir, "history.json")
        file = f
        records = runCatching { if (f.exists()) MatchRecord.decodeAll(f.readText()) else emptyList() }.getOrDefault(emptyList())
    }

    fun add(record: MatchRecord) {
        records = (records + record).takeLast(MAX)
        save()
    }

    fun clear() {
        records = emptyList()
        save()
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, "history.json.tmp")
            tmp.writeText(MatchRecord.encodeAll(records))
            tmp.renameTo(f)
        }
    }
}
