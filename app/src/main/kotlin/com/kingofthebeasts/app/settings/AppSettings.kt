package com.kingofthebeasts.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingofthebeasts.app.audio.GameAudio
import com.kingofthebeasts.app.net.Relay

/** How fast battle animations (and the AI's moves) play. */
enum class AnimationSpeed(val displayName: String, val factor: Float) {
    SLOW("Slow", 0.7f),
    NORMAL("Normal", 1f),
    FAST("Fast", 1.6f),
}

/** Player preferences, kept in SharedPreferences and readable from anywhere as Compose state. */
object AppSettings {
    private var prefs: SharedPreferences? = null

    var musicVolume by mutableFloatStateOf(0.6f)
        private set
    var sfxVolume by mutableFloatStateOf(0.8f)
        private set
    var animationSpeed by mutableStateOf(AnimationSpeed.NORMAL)
        private set
    var keepScreenOn by mutableStateOf(true)
        private set
    /** Top-down 2D board instead of the 2.5D perspective view. */
    var boardFlat by mutableStateOf(false)
        private set
    /** Shown to friends in online games. */
    var playerName by mutableStateOf("Player")
        private set
    /** Another relay server for internet play, overriding the one built in (blank: the built-in one). */
    var serverUrl by mutableStateOf("")
        private set
    /** The relay server internet play uses; blank if none is set up. */
    val relayUrl: String get() = serverUrl.trim().ifBlank { Relay.DEFAULT_URL }
    /** Whether the tutorial has been offered (on first launch) or played. */
    var tutorialOffered by mutableStateOf(false)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        musicVolume = p.getFloat("musicVolume", 0.6f)
        sfxVolume = p.getFloat("sfxVolume", 0.8f)
        animationSpeed = runCatching { AnimationSpeed.valueOf(p.getString("animationSpeed", null)!!) }.getOrDefault(AnimationSpeed.NORMAL)
        keepScreenOn = p.getBoolean("keepScreenOn", true)
        playerName = p.getString("playerName", null) ?: "Player"
        tutorialOffered = p.getBoolean("tutorialOffered", false)
        boardFlat = p.getBoolean("boardFlat", false)
        serverUrl = p.getString("serverUrl", null) ?: ""
        GameAudio.musicVolume = musicVolume
        GameAudio.sfxVolume = sfxVolume
    }

    fun setMusic(v: Float) {
        musicVolume = v
        GameAudio.musicVolume = v
        prefs?.edit()?.putFloat("musicVolume", v)?.apply()
    }

    fun setSfx(v: Float) {
        sfxVolume = v
        GameAudio.sfxVolume = v
        prefs?.edit()?.putFloat("sfxVolume", v)?.apply()
    }

    fun setSpeed(s: AnimationSpeed) {
        animationSpeed = s
        prefs?.edit()?.putString("animationSpeed", s.name)?.apply()
    }

    fun setScreenAwake(on: Boolean) {
        keepScreenOn = on
        prefs?.edit()?.putBoolean("keepScreenOn", on)?.apply()
    }

    fun chooseServer(url: String) {
        serverUrl = url
        prefs?.edit()?.putString("serverUrl", url)?.apply()
    }

    fun chooseBoardView(flat: Boolean) {
        boardFlat = flat
        prefs?.edit()?.putBoolean("boardFlat", flat)?.apply()
    }

    fun markTutorialOffered() {
        tutorialOffered = true
        prefs?.edit()?.putBoolean("tutorialOffered", true)?.apply()
    }

    fun setName(name: String) {
        playerName = name
        prefs?.edit()?.putString("playerName", name)?.apply()
    }
}
