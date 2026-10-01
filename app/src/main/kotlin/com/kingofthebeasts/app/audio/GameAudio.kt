package com.kingofthebeasts.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.kingofthebeasts.app.R

/** Short sound effects. All are CC0 (see the credits in Settings). */
enum class Sfx(val res: Int, val volume: Float = 1f) {
    CLICK(R.raw.sfx_click, 0.5f),
    BACK(R.raw.sfx_back, 0.5f),
    TOGGLE(R.raw.sfx_toggle, 0.6f),
    ERROR(R.raw.sfx_error, 0.6f),
    TURN(R.raw.sfx_turn, 0.7f),
    CARD_PICK(R.raw.sfx_card_pick, 0.8f),
    CARD_PLAY(R.raw.sfx_card_play),
    SHUFFLE(R.raw.sfx_shuffle, 0.8f),
    MOVE(R.raw.sfx_move, 0.7f),
    PLACE(R.raw.sfx_place, 0.8f),
    SWORD1(R.raw.sfx_sword1, 0.8f),
    SWORD2(R.raw.sfx_sword2, 0.8f),
    ARROW(R.raw.sfx_arrow, 0.8f),
    HIT1(R.raw.sfx_hit1, 0.8f),
    HIT2(R.raw.sfx_hit2, 0.8f),
    BLOCK(R.raw.sfx_block, 0.7f),
    DEATH(R.raw.sfx_death, 0.9f),
    SPELL_HARM(R.raw.sfx_spell_harm),
    SPELL_HELP(R.raw.sfx_spell_help, 0.8f),
    SPELL_CONTROL(R.raw.sfx_spell_control, 0.8f),
    ABILITY(R.raw.sfx_ability, 0.7f),
    EQUIP(R.raw.sfx_equip, 0.8f),
    POISON(R.raw.sfx_poison, 0.7f),
    STUN(R.raw.sfx_stun, 0.6f),
    FIELD(R.raw.sfx_field, 0.8f),
}

/** Background music. The menu and battle themes loop; the victory and defeat themes play once. */
enum class Music(val res: Int, val loop: Boolean) {
    MENU(R.raw.music_menu, true),
    BATTLE(R.raw.music_battle, true),
    VICTORY(R.raw.music_victory, false),
    DEFEAT(R.raw.music_defeat, false),
}

/**
 * Plays music and sound effects. Safe to call before [init] (or in tests): it then stays silent.
 * Volumes come from the settings; music pauses while the app is in the background.
 */
object GameAudio {
    private var context: Context? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()

    private var player: MediaPlayer? = null
    private var current: Music? = null
    private var paused = false
    private val handler = Handler(Looper.getMainLooper())
    private var fade: Runnable? = null
    private var level = 0f

    var musicVolume = 0.6f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyMusicVolume()
            // From silent to audible: start the track that should be playing.
            if (field > 0f && player == null && !paused) current?.takeIf { it.loop }?.let { start(it) }
            if (field == 0f) release()
        }
    var sfxVolume = 0.8f
        set(value) { field = value.coerceIn(0f, 1f) }

    fun init(ctx: Context) {
        if (context != null) return
        context = ctx.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        for (s in Sfx.entries) ids[s] = p.load(context, s.res, 1)
        pool = p
    }

    fun play(s: Sfx, volume: Float = 1f) {
        val p = pool ?: return
        val id = ids[s] ?: return
        val v = (sfxVolume * s.volume * volume).coerceIn(0f, 1f)
        if (v <= 0f || id !in loaded) return
        p.play(id, v, v, 1, 0, 1f)
    }

    /** Switches to [track] with a short crossfade; the same track keeps playing uninterrupted. */
    fun music(track: Music?) {
        if (track == current && (player != null || musicVolume == 0f)) return
        current = track
        val old = player
        player = null
        if (old != null) fadeTo(old, 0f, 450) { runCatching { old.release() } }
        if (track != null && musicVolume > 0f && !paused) start(track)
    }

    fun pause() {
        paused = true
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun resume() {
        paused = false
        val p = player
        if (p != null) runCatching { p.start() } else current?.let { if (musicVolume > 0f) start(it) }
    }

    private fun start(track: Music) {
        val ctx = context ?: return
        val mp = runCatching { MediaPlayer.create(ctx, track.res) }.getOrNull() ?: return
        mp.isLooping = track.loop
        mp.setVolume(0f, 0f)
        if (!track.loop) mp.setOnCompletionListener { if (player === it) { it.release(); player = null } }
        player = mp
        level = 0f
        runCatching { mp.start() }
        fadeTo(mp, 1f, 700)
    }

    private fun release() {
        runCatching { player?.release() }
        player = null
    }

    private fun applyMusicVolume() {
        val v = level * musicVolume
        runCatching { player?.setVolume(v, v) }
    }

    /** Fades [mp] to [target] (0..1 of the music volume) over [ms], then runs [done]. */
    private fun fadeTo(mp: MediaPlayer, target: Float, ms: Long, done: () -> Unit = {}) {
        val steps = 15
        val from = if (mp === player) level else musicVolume.takeIf { it > 0f }?.let { 1f } ?: 0f
        if (mp === player) fade?.let { handler.removeCallbacks(it) }
        var i = 0
        val r = object : Runnable {
            override fun run() {
                i++
                val k = from + (target - from) * i / steps
                if (mp === player) {
                    level = k
                    applyMusicVolume()
                } else {
                    runCatching { mp.setVolume(k * musicVolume, k * musicVolume) }
                }
                if (i < steps) handler.postDelayed(this, ms / steps) else done()
            }
        }
        if (mp === player) fade = r
        handler.post(r)
    }
}
