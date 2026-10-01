package com.kingofthebeasts.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingofthebeasts.app.audio.GameAudio

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
    /** Shown to friends in online games. */
    var playerName by mutableStateOf("Player")
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        musicVolume = p.getFloat("musicVolume", 0.6f)
        sfxVolume = p.getFloat("sfxVolume", 0.8f)
        animationSpeed = runCatching { AnimationSpeed.valueOf(p.getString("animationSpeed", null)!!) }.getOrDefault(AnimationSpeed.NORMAL)
        keepScreenOn = p.getBoolean("keepScreenOn", true)
        playerName = p.getString("playerName", null) ?: "Player"
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

    fun setName(name: String) {
        playerName = name
        prefs?.edit()?.putString("playerName", name)?.apply()
    }
}
