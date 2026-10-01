package com.kingofthebeasts.app

import org.robolectric.RuntimeEnvironment
import com.kingofthebeasts.app.audio.GameAudio
import com.kingofthebeasts.app.audio.Music
import com.kingofthebeasts.app.audio.Sfx
import com.kingofthebeasts.app.settings.AnimationSpeed
import com.kingofthebeasts.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTest {
    /** Settings survive a restart and are handed to the audio player. */
    @Test fun settingsArePersistedAndApplied() {
        val context = RuntimeEnvironment.getApplication()
        AppSettings.load(context)
        AppSettings.setMusic(0.25f)
        AppSettings.setSfx(0f)
        AppSettings.setSpeed(AnimationSpeed.FAST)
        AppSettings.setScreenAwake(false)

        AppSettings.load(context)
        assertEquals(0.25f, AppSettings.musicVolume)
        assertEquals(0f, AppSettings.sfxVolume)
        assertEquals(AnimationSpeed.FAST, AppSettings.animationSpeed)
        assertFalse(AppSettings.keepScreenOn)
        assertEquals(0.25f, GameAudio.musicVolume)
    }

    /** Every sound and track is packaged, and playing them never crashes. */
    @Test fun audioPlaysSafely() {
        val context = RuntimeEnvironment.getApplication()
        for (s in Sfx.entries) context.resources.openRawResourceFd(s.res).close()
        for (m in Music.entries) context.resources.openRawResourceFd(m.res).close()
        GameAudio.init(context)
        Sfx.entries.forEach { GameAudio.play(it) }
        GameAudio.music(Music.BATTLE)
        GameAudio.pause()
        GameAudio.resume()
        GameAudio.music(Music.MENU)
    }
}
