package com.kingofthebeasts.app.game

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.res.ResourcesCompat
import com.kingofthebeasts.app.R
import com.kingofthebeasts.app.audio.GameAudio
import com.kingofthebeasts.app.audio.Sfx
import com.kingofthebeasts.app.settings.AppSettings
import com.kingofthebeasts.app.ui.CardArt
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.game.Board
import com.kingofthebeasts.core.game.CastLook
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameEvent
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.Pos
import com.kingofthebeasts.core.game.UnitState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Perspective projection of the 8×8 board plane, seen by a camera that can orbit the
 * board's centre. Board coordinates: x to the right, y away from the human player (row 0 is
 * nearest at angle 0). [angleDeg] turns the board around its centre; the camera always looks
 * from the bottom of the screen, so 180° shows the board from the opponent's side.
 * With [flat] the camera looks straight down instead: a top-down 2D board.
 */
class BoardProjection(width: Float, height: Float, angleDeg: Float = 0f, val flat: Boolean = false) {
    private val cos = cos(Math.toRadians(angleDeg.toDouble())).toFloat()
    private val sin = sin(Math.toRadians(angleDeg.toDouble())).toFloat()
    /** Half-extent of the rotated board along the view axes (4 when square-on, ~5.7 at 45°). */
    private val half = Board.SIZE / 2f * (abs(cos) + abs(sin))
    private val span = 2 * half
    private val z0 = 1.5f * span
    /**
     * Apparent depth of a near square relative to its width. Tall views get a steeper,
     * more top-down camera so the board fills the space.
     */
    private var tilt = MIN_TILT
    val b: Float
    val a: Float
    private val cx = width / 2f
    private val horizonY: Float
    /** Top-down view: screen pixels per square and the board centre's height on screen. */
    private val cell = min(width * 0.94f, height * 0.9f) / span
    private val cy = height / 2f

    /** How much "height above the board" shows on screen (none looking straight down, a little for effects). */
    val heightFactor: Float get() = if (flat) 0.3f else 1f

    init {
        var bb = width * 0.94f * z0 / span
        val depth = bb * z0 * (1f / z0 - 1f / (z0 + span))
        val spare = height - (needHeight(bb) - tilt * depth)
        tilt = (spare / depth).coerceIn(MIN_TILT, MAX_TILT)
        val need = needHeight(bb)
        if (need > height) bb *= height / need
        b = bb
        a = bb * tilt * z0
        val boardH = a * (1f / z0 - 1f / (z0 + span))
        val headroom = 1.15f * b / (z0 + span - 0.5f)
        val total = boardH + headroom + slab
        val top = (height - total) / 2f + headroom
        horizonY = top - a / (z0 + span)
    }

    private fun needHeight(bb: Float): Float {
        val aa = bb * tilt * z0
        return aa * (1f / z0 - 1f / (z0 + span)) + 1.15f * bb / (z0 + span - 0.5f) + 0.25f * bb / z0 + 6f
    }

    val slab: Float get() = if (flat) 0f else 0.25f * b / z0

    private companion object {
        const val MIN_TILT = 0.55f
        const val MAX_TILT = 0.9f
    }

    /** View-space coordinates: u to the right on screen, v away from the camera (−half = nearest). */
    private fun toView(bx: Float, by: Float): Pair<Float, Float> {
        val dx = bx - Board.SIZE / 2f
        val dy = by - Board.SIZE / 2f
        return (dx * cos - dy * sin) to (dx * sin + dy * cos)
    }

    /** Distance from the camera, used to sort things far-to-near. */
    fun depth(bx: Float, by: Float): Float = toView(bx, by).second

    fun project(bx: Float, by: Float): Offset {
        val (u, v) = toView(bx, by)
        if (flat) return Offset(cx + u * cell, cy - v * cell)
        val z = z0 + v + half
        return Offset(cx + u * b / z, horizonY + a / z)
    }

    /** Screen height of a [len]-square step away from the camera at board point ([bx], [by]). */
    fun depthSpan(bx: Float, by: Float, len: Float): Float {
        if (flat) return len * cell
        val z = z0 + depth(bx, by) + half
        return a * len / (z * z)
    }

    /** Screen pixels per board square at board point ([bx], [by]). */
    fun scale(bx: Float, by: Float): Float = if (flat) cell else b / (z0 + depth(bx, by) + half)

    /** The board square under a screen point (as fractional board coordinates), or null above the horizon. */
    fun unproject(o: Offset): Offset? {
        if (flat) {
            val u = (o.x - cx) / cell
            val v = (cy - o.y) / cell
            return Offset(u * cos + v * sin + Board.SIZE / 2f, -u * sin + v * cos + Board.SIZE / 2f)
        }
        val dy = o.y - horizonY
        if (dy <= 0f) return null
        val z = a / dy
        val u = (o.x - cx) * z / b
        val v = z - z0 - half
        return Offset(u * cos + v * sin + Board.SIZE / 2f, -u * sin + v * cos + Board.SIZE / 2f)
    }

    /** Whether a board edge with outward normal (nx, ny) faces the camera. */
    fun facesCamera(nx: Float, ny: Float): Boolean = !flat && nx * sin + ny * cos < -0.01f

    fun quad(x: Int, y: Int, inset: Float = 0f): Path = Path().apply {
        val p0 = project(x + inset, y + inset)
        val p1 = project(x + 1 - inset, y + inset)
        val p2 = project(x + 1 - inset, y + 1 - inset)
        val p3 = project(x + inset, y + 1 - inset)
        moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
    }
}

private class HitBox(val rect: Rect, val pos: Pos)

/** Animations made from the events up to [seq], and when each moving unit should start sliding. */
private class FxBatch(val seq: Int, val effects: List<Fx>, val moveDelay: Map<Int, Long>)

@Composable
fun BoardView(
    vm: GameViewModel,
    highlights: Highlights,
    modifier: Modifier = Modifier,
    angle: Float = 0f,
    /** Top-down 2D view instead of the 2.5D one. */
    flat: Boolean = false,
    onRotate: (Float) -> Unit = {},
    onInspect: (UnitState) -> Unit = {},
) {
    val state = vm.state
    val version = vm.version
    val context = LocalContext.current
    val resources = LocalResources.current
    val boardTexture = ImageBitmap.imageResource(R.drawable.board_texture)
    val arts = remember { HashMap<String, ImageBitmap>() }
    fun art(id: String) = arts.getOrPut(id) { ImageBitmap.imageResource(resources, CardArt.res(context, id)) }
    val handFace = remember { ResourcesCompat.getFont(context, R.font.kalam_bold) ?: Typeface.DEFAULT_BOLD }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = handFace; textAlign = Paint.Align.CENTER } }
    val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val matrix = remember { Matrix() }
    val projHolder = remember { arrayOfNulls<BoardProjection>(1) }
    val hitBoxes = remember { mutableListOf<HitBox>() }
    val currentHighlights = rememberUpdatedState(highlights)

    val positions = remember { HashMap<Int, Animatable<Offset, AnimationVector2D>>() }
    val fx = remember { mutableStateListOf<Fx>() }
    var now by remember { mutableLongStateOf(0L) }
    // After a resume, don't replay the animations of everything that happened before.
    val seen = remember { intArrayOf(vm.resumedEventSeq) }

    // Animation clock: ticks only while something is animating, at the speed chosen in Settings.
    // Newly queued effects start on the next frame.
    LaunchedEffect(Unit) {
        var clock = 0f
        var last = -1L
        while (true) {
            if (fx.isEmpty()) {
                snapshotFlow { fx.size }.first { it > 0 }
                last = -1L
            }
            withFrameMillis { t ->
                if (last >= 0) clock += (t - last) * AppSettings.animationSpeed.factor
                last = t
                val c = clock.toLong()
                for (f in fx) if (f.base < 0) f.base = c
                for (f in fx) if (f is Cue && !f.played && f.started(c)) {
                    f.played = true
                    GameAudio.play(f.sfx, f.volume)
                }
                now = c
                fx.removeAll { it.done(c) }
            }
        }
    }

    // New events become animations as part of the recomposition, so they are queued before the board
    // redraws: a fallen unit keeps standing, and a new one stays hidden, until its moment comes.
    val batch = remember(version) {
        val seenSeq = seen[0]
        val moveDelay = HashMap<Int, Long>()
        // Lay the new events out on a timeline so cause comes before effect: a spell flies, then hits.
        val queued = mutableListOf<Fx>()
        var t = 0L
        fun at(p: Pos) = Offset(p.x + 0.5f, p.y + 0.5f)
        val rows = HashMap<Pos, Int>()
        var strikes = 0
        var lastLook: CastLook? = null
        fun sound(sfx: Sfx?, at: Long = t, volume: Float = 1f) { if (sfx != null) queued += Cue(at, sfx, volume) }
        fun text(text: String, color: Color, p: Pos) {
            val row = rows[p] ?: 0
            rows[p] = row + 1
            queued += FloatText(t, text, color, at(p), row)
        }
        for (e in state.events) {
            if (e.seq <= seenSeq) continue
            when (e) {
                is GameEvent.Cast -> {
                    val color = lookColor(e.look)
                    val target = e.at?.let(::at) ?: Offset(Board.SIZE / 2f, Board.SIZE / 2f)
                    val radius = if (e.radius > 0) e.radius + 0.5f else 0.5f
                    if (e.from != null) {
                        // an ability: a glow at the user, then an orb to the target
                        lastLook = e.look
                        sound(Sfx.ABILITY)
                        queued += Burst(t, at(e.from!!), color, 0.35f)
                        t += 120
                        if (e.at != null && e.at != e.from) {
                            queued += Bolt(t, at(e.from!!), target, color, arrow = false)
                            t += 300
                        }
                        queued += Burst(t, target, color, radius)
                        sound(lookSound(e.look))
                        t += 140
                    } else {
                        // a card from the hand flies in from its owner's side
                        lastLook = e.look
                        sound(Sfx.CARD_PLAY)
                        // player 0's side of the board is row 1, player 1's is row 8
                        val side = if (e.player == 0) -1.5f else Board.SIZE + 1.5f
                        queued += CardFly(t, e.cardId, Offset(target.x, side), target, color)
                        t += 400
                        sound(lookSound(e.look))
                        when (e.look) {
                            CastLook.FIELD -> queued += FieldWave(t, color)
                            CastLook.SUMMON -> {}
                            else -> queued += Burst(t, target, color, radius)
                        }
                        t += 120
                    }
                }
                is GameEvent.Attacked -> {
                    val dir = Offset((e.to.x - e.from.x).toFloat(), (e.to.y - e.from.y).toFloat())
                    if (e.from.distanceTo(e.to) <= 1) {
                        sound(if (strikes++ % 2 == 0) Sfx.SWORD1 else Sfx.SWORD2, t + 60)
                        queued += Lunge(t, e.unitId, dir.normalized(0.4f))
                        t += 170
                    } else {
                        queued += Lunge(t, e.unitId, dir.normalized(-0.1f))
                        queued += Bolt(t + 60, at(e.from), at(e.to), Ink.Line, arrow = true)
                        sound(Sfx.ARROW, t + 40)
                        t += 400
                    }
                }
                is GameEvent.Damaged -> {
                    queued += Hit(t, e.unitId)
                    sound(if (e.amount == 0) Sfx.BLOCK else if (strikes % 2 == 0) Sfx.HIT1 else Sfx.HIT2)
                    text(if (e.amount > 0) "-${e.amount}" else "0", Ink.Attack, e.pos)
                    t += 120
                }
                is GameEvent.Healed -> {
                    queued += Burst(t, at(e.pos), Ink.Heal, 0.4f)
                    // healing spells already chimed when they landed
                    if (lastLook != CastLook.HELP) sound(Sfx.SPELL_HELP, volume = 0.6f)
                    text("+${e.amount}", Ink.Heal, e.pos)
                    t += 120
                }
                is GameEvent.Status -> {
                    when {
                        e.text == "Stunned" -> sound(Sfx.STUN)
                        e.text == "Poisoned" -> sound(Sfx.POISON)
                        e.text.startsWith("Blocked") || e.text == "Immune" -> sound(Sfx.BLOCK, volume = 0.7f)
                    }
                    text(e.text, Ink.Target, e.pos)
                    t += 120
                }
                is GameEvent.Died -> {
                    queued += Ghost(t, e.cardId, e.owner, at(e.pos))
                    sound(Sfx.DEATH, t + 300)
                    t += 140
                }
                is GameEvent.Arrived -> {
                    queued += Appear(t, e.unitId)
                    sound(Sfx.PLACE, t + 250, if (e.token) 0.7f else 1f)
                    queued += Burst(t + 250, at(e.pos), Ink.Faded, 0.45f)
                    t += if (e.token) 120 else 200
                }
                is GameEvent.Moved -> {
                    moveDelay[e.unitId] = t
                    queued += Hop(t, e.unitId)
                    sound(Sfx.MOVE)
                    t += 160
                }
                is GameEvent.Announce -> {}
            }
        }
        FxBatch(state.eventSeq, queued, moveDelay)
    }
    SideEffect {
        if (batch.seq > seen[0]) {
            seen[0] = batch.seq
            fx += batch.effects
        }
    }

    LaunchedEffect(version) {
        val alive = state.units.map { it.id }.toSet()
        positions.keys.retainAll(alive)
        for (u in state.units) {
            val target = Offset(u.pos.x + 0.5f, u.pos.y + 0.5f)
            val anim = positions[u.id]
            if (anim == null) positions[u.id] = Animatable(target, Offset.VectorConverter)
            else if (anim.targetValue != target) {
                val speed = AppSettings.animationSpeed.factor
                val delayMs = ((batch.moveDelay[u.id] ?: 0L) / speed).toInt()
                launch { anim.animateTo(target, tween((380 / speed).toInt(), delayMillis = delayMs)) }
            }
        }
    }

    Canvas(
        modifier
            // Two-finger twist turns the board.
            .pointerInput(Unit) { detectTransformGestures { _, _, _, rotation -> if (rotation != 0f) onRotate(-rotation) } }
            .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { o ->
                    // Long press a unit to inspect its card and live stats.
                    val pos = hitBoxes.firstOrNull { it.rect.contains(o) }?.pos
                        ?: projHolder[0]?.unproject(o)?.let { b -> Pos(floor(b.x).toInt(), floor(b.y).toInt()) }
                    pos?.takeIf { it.onBoard }?.let { state.unitAt(it) }?.let(onInspect)
                },
            ) { o ->
                // A highlighted or occupied square under the finger wins; otherwise a standee
                // reaching up over the square behind it catches the tap.
                val tile = projHolder[0]?.unproject(o)?.let { b -> Pos(floor(b.x).toInt(), floor(b.y).toInt()) }
                    ?.takeIf { it.onBoard }
                val h = currentHighlights.value
                val preferTile = tile != null &&
                    (tile in h.move || tile in h.attack || tile in h.target || state.unitAt(tile) != null)
                val hit = if (preferTile) tile else hitBoxes.firstOrNull { it.rect.contains(o) }?.pos ?: tile
                if (hit != null) vm.onTileTapped(hit)
            }
        },
    ) {
        @Suppress("UNUSED_EXPRESSION") version // redraw whenever the game changes
        val clock = now
        val effects = fx.toList()
        val proj = BoardProjection(size.width, size.height, angle, flat)
        projHolder[0] = proj

        drawBoardBase(proj, boardTexture, matrix, bitmapPaint)
        drawZones(proj, state.phase == Phase.DEPLOY, vm.human)
        drawHighlights(proj, highlights)
        drawCoordinates(proj, textPaint)

        // Units (and fading fallen ones), far to near, so nearer standees overlap farther ones.
        hitBoxes.clear()
        val drawn = state.units.filter { it.alive }.map { u ->
            Triple(u, null as Ghost?, positions[u.id]?.value ?: Offset(u.pos.x + 0.5f, u.pos.y + 0.5f))
        } + effects.filterIsInstance<Ghost>().map { Triple(null, it, it.at) }
        val boxes = mutableListOf<HitBox>()
        for ((unit, ghost, home) in drawn.sortedByDescending { proj.depth(it.third.x, it.third.y) }) {
            if (ghost != null) {
                drawGhost(proj, ghost, clock, art(ghost.cardId), if (ghost.owner == vm.human) Ink.You else Ink.Enemy)
                continue
            }
            val u = unit!!
            val pose = poseOf(u.id, effects, clock)
            if (pose.hidden) continue
            val bp = home + pose.boardShift
            val s0 = proj.scale(bp.x, bp.y)
            val restBase = proj.project(bp.x, bp.y)
            val team = if (u.owner == vm.human) Ink.You else Ink.Enemy
            if (proj.flat) {
                // Top-down view: a round token instead of an upright standee.
                val r = 0.42f * s0 * pose.scale
                val c = restBase + Offset(pose.shake * s0, -pose.lift * s0 * proj.heightFactor)
                drawToken(
                    u, c, r, s0, team, art(u.def.id), highlights.selected == u.pos, pose.flash,
                    stunned = u.stun > 0, hidden = GameEngine.isHidden(state, u),
                    attack = GameEngine.attackOf(state, u), textPaint = textPaint,
                )
                boxes += HitBox(Rect(c.x - r, c.y - r, c.x + r, c.y + r), u.pos)
                continue
            }
            withTransform({ translate(pose.shake * s0, 0f) }) {
                val s = s0
                val base = restBase
                // Apparent height of the base disc: the screen height of a 0.68-square step in depth.
                val depth = proj.depthSpan(bp.x, bp.y, 0.68f)

                drawOval(Color.Black.copy(alpha = 0.22f), Offset(base.x - 0.4f * s, base.y - depth / 2 + 0.03f * s), Size(0.8f * s, depth))
                drawOval(team.copy(alpha = 0.55f), Offset(base.x - 0.33f * s, base.y - depth * 0.42f), Size(0.66f * s, depth * 0.84f))
                drawOval(Ink.Line.copy(alpha = 0.8f), Offset(base.x - 0.33f * s, base.y - depth * 0.42f), Size(0.66f * s, depth * 0.84f), style = Stroke(0.02f * s))
                if (u.shield > 0) {
                    drawOval(Ink.Move.copy(alpha = 0.8f), Offset(base.x - 0.42f * s, base.y - depth * 0.55f), Size(0.84f * s, depth * 1.1f), style = Stroke(0.05f * s))
                }

                // The standee itself hops, drops in and bounces; its base stays on the board.
                withTransform({
                    translate(0f, -pose.lift * s0)
                    scale(pose.scale, pose.scale, restBase)
                }) {
                    val sw = 0.6f * s
                    val sh = 0.92f * s
                    val rect = Rect(base.x - sw / 2, base.y - sh, base.x + sw / 2, base.y - 0.02f * s)
                    val arch = Path().apply {
                        moveTo(rect.left, rect.bottom)
                        lineTo(rect.left, rect.top + sw / 2)
                        arcTo(Rect(rect.left, rect.top, rect.right, rect.top + sw), 180f, 180f, false)
                        lineTo(rect.right, rect.bottom)
                        close()
                    }
                    // standee thickness
                    translate(0.035f * s, 0.02f * s) { drawPath(arch, Color(0xFF4A3B2E)) }
                    val img = art(u.def.id)
                    val srcH = (img.height * 0.5f).roundToInt()
                    val srcW = min(img.width, (srcH * sw / sh).roundToInt())
                    val srcTop = (img.height * 0.36f - srcH / 2f).roundToInt().coerceIn(0, img.height - srcH)
                    clipPath(arch) {
                        drawImage(
                            img,
                            srcOffset = IntOffset((img.width - srcW) / 2, srcTop), srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                        )
                        if (u.stun > 0) drawRect(Color(0x55A0A0FF), rect.topLeft, rect.size)
                        // Hidden units look shadowy.
                        if (GameEngine.isHidden(state, u)) drawRect(Color(0x66302838), rect.topLeft, rect.size)
                        if (pose.flash > 0f) drawRect(Ink.Attack.copy(alpha = pose.flash), rect.topLeft, rect.size)
                    }
                    val selected = highlights.selected == u.pos
                    drawPath(arch, if (selected) Ink.Gold else team, style = Stroke(if (selected) 0.09f * s else 0.055f * s, join = StrokeJoin.Round))
                    drawPath(arch, Ink.Line, style = Stroke(0.014f * s, join = StrokeJoin.Round))

                    // attack and health badges
                    val r = 0.14f * s
                    val atk = GameEngine.attackOf(state, u)
                    badge(Offset(rect.left + 0.02f * s, rect.bottom - 0.12f * s), r, Ink.Attack, atk.toString(), textPaint)
                    badge(Offset(rect.right - 0.02f * s, rect.bottom - 0.12f * s), r, if (u.hp < u.maxHp) Color(0xFFD9822B) else Ink.Heal, u.hp.toString(), textPaint)
                    if (u.isKing) label("♛", Offset(base.x, rect.top - 0.02f * s), 0.34f * s, Ink.Gold, textPaint, outline = true)
                    var icons = ""
                    if (u.stun > 0) icons += "💫"
                    if (u.poisonTurns > 0) icons += "☠"
                    if (icons.isNotEmpty()) label(icons, Offset(rect.right, rect.top + 0.18f * s), 0.24f * s, Ink.Heal, textPaint, outline = true)
                    boxes += HitBox(Rect(rect.left - r, rect.top, rect.right + r, base.y + depth / 2), u.pos)
                }
            }
        }
        // nearest first for hit testing
        hitBoxes += boxes.asReversed()

        for (f in effects) when (f) {
            is Burst -> drawBurst(proj, f, clock)
            is FieldWave -> drawFieldWave(proj, f, clock)
            else -> {}
        }
        for (f in effects) when (f) {
            is Bolt -> drawBolt(proj, f, clock)
            is CardFly -> drawCardFly(proj, f, clock, art(f.cardId))
            is FloatText -> drawFloatText(proj, f, clock) { text, at, size, color -> label(text, at, size, color, textPaint, outline = true) }
            else -> {}
        }
    }
}

/** A unit seen from above: its art in a round, team-coloured token with attack and health badges. */
private fun DrawScope.drawToken(
    u: UnitState, c: Offset, r: Float, s: Float, team: Color, img: ImageBitmap, selected: Boolean, flash: Float,
    stunned: Boolean, hidden: Boolean, attack: Int, textPaint: Paint, alpha: Float = 1f,
) {
    drawCircle(Color.Black.copy(alpha = 0.25f * alpha), r * 1.02f, c + Offset(0.04f * s, 0.05f * s))
    drawCircle(team.copy(alpha = alpha), r, c)
    val inner = r * 0.84f
    val clip = Path().apply { addOval(Rect(c.x - inner, c.y - inner, c.x + inner, c.y + inner)) }
    clipPath(clip) {
        val side = min(img.width, (img.height * 0.62f).roundToInt())
        val top = (img.height * 0.36f - side / 2f).roundToInt().coerceIn(0, img.height - side)
        drawImage(
            img, srcOffset = IntOffset((img.width - side) / 2, top), srcSize = IntSize(side, side),
            dstOffset = IntOffset((c.x - inner).roundToInt(), (c.y - inner).roundToInt()),
            dstSize = IntSize((2 * inner).roundToInt(), (2 * inner).roundToInt()), alpha = alpha,
        )
        val box = Rect(c.x - inner, c.y - inner, c.x + inner, c.y + inner)
        if (stunned) drawRect(Color(0x55A0A0FF), box.topLeft, box.size)
        if (hidden) drawRect(Color(0x66302838), box.topLeft, box.size)
        if (flash > 0f) drawRect(Ink.Attack.copy(alpha = flash), box.topLeft, box.size)
    }
    drawCircle(Ink.Line.copy(alpha = alpha), inner, c, style = Stroke(0.018f * s))
    drawCircle(if (selected) Ink.Gold else Ink.Line.copy(alpha = alpha), r, c, style = Stroke(if (selected) 0.07f * s else 0.02f * s))
    if (u.shield > 0) drawCircle(Ink.Move.copy(alpha = 0.85f * alpha), r * 1.12f, c, style = Stroke(0.05f * s))
    if (alpha < 1f) return
    val br = 0.13f * s
    badge(Offset(c.x - r * 0.78f, c.y + r * 0.62f), br, Ink.Attack, attack.toString(), textPaint)
    badge(Offset(c.x + r * 0.78f, c.y + r * 0.62f), br, if (u.hp < u.maxHp) Color(0xFFD9822B) else Ink.Heal, u.hp.toString(), textPaint)
    if (u.isKing) label("♛", Offset(c.x, c.y - r * 0.72f), 0.3f * s, Ink.Gold, textPaint, outline = true)
    var icons = ""
    if (u.stun > 0) icons += "💫"
    if (u.poisonTurns > 0) icons += "☠"
    if (icons.isNotEmpty()) label(icons, Offset(c.x + r * 0.8f, c.y - r * 0.5f), 0.22f * s, Ink.Heal, textPaint, outline = true)
}

/** A fallen unit: it stands until its moment, then topples backwards, sinks and fades. */
private fun DrawScope.drawGhost(proj: BoardProjection, g: Ghost, now: Long, img: ImageBitmap, team: Color) {
    val t = g.t(now)
    if (proj.flat) {
        // From above, the fallen token shrinks, darkens and fades away.
        val s = proj.scale(g.at.x, g.at.y)
        val c = proj.project(g.at.x, g.at.y)
        val k = 1f - t
        val r = 0.42f * s * (1f - 0.4f * t)
        drawCircle(Color.Black.copy(alpha = 0.25f * k), r * 1.02f, c + Offset(0.04f * s, 0.05f * s))
        drawCircle(team.copy(alpha = k), r, c)
        val inner = r * 0.84f
        clipPath(Path().apply { addOval(Rect(c.x - inner, c.y - inner, c.x + inner, c.y + inner)) }) {
            val side = min(img.width, (img.height * 0.62f).roundToInt())
            val top = (img.height * 0.36f - side / 2f).roundToInt().coerceIn(0, img.height - side)
            drawImage(
                img, srcOffset = IntOffset((img.width - side) / 2, top), srcSize = IntSize(side, side),
                dstOffset = IntOffset((c.x - inner).roundToInt(), (c.y - inner).roundToInt()),
                dstSize = IntSize((2 * inner).roundToInt(), (2 * inner).roundToInt()), alpha = k,
            )
            drawRect(Color(0xFF3A3A3A).copy(alpha = 0.5f * min(1f, t * 3f) * k), Offset(c.x - inner, c.y - inner), Size(2 * inner, 2 * inner))
        }
        return
    }
    val k = 1f - t
    val s = proj.scale(g.at.x, g.at.y)
    val base = proj.project(g.at.x, g.at.y)
    val depth = proj.depthSpan(g.at.x, g.at.y, 0.68f)
    drawOval(Color.Black.copy(alpha = 0.22f * k), Offset(base.x - 0.4f * s, base.y - depth / 2 + 0.03f * s), Size(0.8f * s, depth))
    val sw = 0.6f * s
    val sh = 0.92f * s * (1f - 0.35f * t)
    val rect = Rect(base.x - sw / 2, base.y - sh + 0.15f * s * t, base.x + sw / 2, base.y - 0.02f * s + 0.15f * s * t)
    val arch = Path().apply {
        moveTo(rect.left, rect.bottom)
        lineTo(rect.left, rect.top + sw / 2)
        arcTo(Rect(rect.left, rect.top, rect.right, rect.top + sw), 180f, 180f, false)
        lineTo(rect.right, rect.bottom)
        close()
    }
    rotate(-70f * t * t, Offset(base.x, base.y)) {
        val srcH = (img.height * 0.5f).roundToInt()
        val srcW = min(img.width, (srcH * sw / (0.92f * s)).roundToInt())
        val srcTop = (img.height * 0.36f - srcH / 2f).roundToInt().coerceIn(0, img.height - srcH)
        clipPath(arch) {
            drawImage(
                img, srcOffset = IntOffset((img.width - srcW) / 2, srcTop), srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()), alpha = k,
            )
            drawRect(Color(0xFF3A3A3A).copy(alpha = 0.5f * min(1f, t * 3f) * k), rect.topLeft, rect.size)
        }
        drawPath(arch, team.copy(alpha = k), style = Stroke(0.055f * s, join = StrokeJoin.Round))
        drawPath(arch, Ink.Line.copy(alpha = k), style = Stroke(0.014f * s, join = StrokeJoin.Round))
    }
    // dust puffs where it falls
    if (t > 0.3f) {
        val d = (t - 0.3f) / 0.7f
        for (i in 0 until 6) {
            val a = i / 6f * 2f * Math.PI.toFloat()
            val p = proj.project(g.at.x + cos(a) * 0.35f * d, g.at.y + sin(a) * 0.35f * d) - Offset(0f, 0.12f * s * d)
            drawCircle(Ink.Faded.copy(alpha = 0.45f * (1f - d)), 0.08f * s * (0.6f + d), p)
        }
    }
}

private inline fun DrawScope.translate(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(dx, dy)
    block()
    drawContext.transform.translate(-dx, -dy)
}

private fun DrawScope.drawBoardBase(proj: BoardProjection, texture: ImageBitmap, matrix: Matrix, paint: Paint) {
    val n = Board.SIZE.toFloat()
    // Board corners in board coordinates; the texture's top-left is the far-left corner at angle 0.
    val tl = proj.project(0f, n)
    val tr = proj.project(n, n)
    val br = proj.project(n, 0f)
    val bl = proj.project(0f, 0f)

    // soft watercolor shadow under the board
    val shadow = Path().apply {
        listOf(tl, tr, br, bl).forEachIndexed { i, c -> if (i == 0) moveTo(c.x, c.y + 10) else lineTo(c.x, c.y + 10) }
        close()
    }
    drawPath(shadow, Color(0x22402A10))
    drawPath(shadow, Color(0x14402A10), style = Stroke(18f, join = StrokeJoin.Round))

    // The slab's side faces that point towards the camera (outward normal per edge).
    val edges = listOf(
        Triple(Offset(0f, 0f), Offset(n, 0f), Offset(0f, -1f)),
        Triple(Offset(n, 0f), Offset(n, n), Offset(1f, 0f)),
        Triple(Offset(n, n), Offset(0f, n), Offset(0f, 1f)),
        Triple(Offset(0f, n), Offset(0f, 0f), Offset(-1f, 0f)),
    )
    for ((a, b, normal) in edges) {
        if (!proj.facesCamera(normal.x, normal.y)) continue
        val pa = proj.project(a.x, a.y)
        val pb = proj.project(b.x, b.y)
        val ta = 0.25f * proj.scale(a.x, a.y)
        val tb = 0.25f * proj.scale(b.x, b.y)
        val face = Path().apply {
            moveTo(pa.x, pa.y); lineTo(pb.x, pb.y); lineTo(pb.x, pb.y + tb); lineTo(pa.x, pa.y + ta); close()
        }
        drawPath(face, Color(0xFF9C7A55))
        drawPath(face, Color(0x33FFFFFF), style = Stroke(min(ta, tb) * 0.25f))
        drawPath(face, Ink.Line, style = Stroke(2.2f, join = StrokeJoin.Round))
    }

    val bmp = texture.asAndroidBitmap()
    val w = bmp.width.toFloat()
    val h = bmp.height.toFloat()
    matrix.setPolyToPoly(
        floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0,
        floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y), 0, 4,
    )
    drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, matrix, paint) }

    // wobbly ink outline around the playing surface
    val rnd = Random(5)
    val outline = Path()
    val corners = listOf(tl, tr, br, bl, tl)
    outline.moveTo(tl.x, tl.y)
    for (i in 1 until corners.size) {
        val a = corners[i - 1]
        val b = corners[i]
        val mid = Offset((a.x + b.x) / 2 + rnd.nextFloat() * 3f - 1.5f, (a.y + b.y) / 2 + rnd.nextFloat() * 3f - 1.5f)
        outline.quadraticTo(mid.x, mid.y, b.x, b.y)
    }
    drawPath(outline, Ink.Line, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawZones(proj: BoardProjection, deploy: Boolean, human: Int) {
    if (!deploy) return
    for (y in 0 until Board.SIZE) for (x in 0 until Board.SIZE) {
        val c = when {
            Board.isDeployZone(human, Pos(x, y)) -> Ink.You.copy(alpha = 0.13f)
            Board.isDeployZone(1 - human, Pos(x, y)) -> Ink.Enemy.copy(alpha = 0.10f)
            else -> continue
        }
        drawPath(proj.quad(x, y), c)
    }
}

private fun DrawScope.drawHighlights(proj: BoardProjection, h: Highlights) {
    fun mark(p: Pos, color: Color) {
        val q = proj.quad(p.x, p.y, 0.07f)
        drawPath(q, color.copy(alpha = 0.33f))
        drawPath(q, color.copy(alpha = 0.9f), style = Stroke(2.6f, join = StrokeJoin.Round))
    }
    h.move.forEach { mark(it, Ink.Move) }
    h.target.forEach { mark(it, Ink.Target) }
    h.attack.forEach { mark(it, Ink.Attack) }
    h.selected?.let { drawPath(proj.quad(it.x, it.y, 0.04f), Ink.Gold, style = Stroke(4f, join = StrokeJoin.Round)) }
}

private fun DrawScope.drawCoordinates(proj: BoardProjection, paint: Paint) {
    // Files (a–h) just outside row 1, ranks (1–8) just outside file a; they turn with the board.
    for (x in 0 until Board.SIZE) {
        val p = proj.project(x + 0.5f, -0.3f)
        label(('A' + x).toString(), Offset(p.x, p.y + 0.1f * proj.scale(x + 0.5f, -0.3f)), 0.24f * proj.scale(x + 0.5f, -0.3f), Ink.Faded, paint)
    }
    for (y in 0 until Board.SIZE) {
        val p = proj.project(-0.3f, y + 0.5f)
        val s = proj.scale(-0.3f, y + 0.5f)
        label((y + 1).toString(), Offset(p.x, p.y + 0.1f * s), 0.24f * s, Ink.Faded, paint)
    }
}

private fun DrawScope.badge(center: Offset, r: Float, color: Color, text: String, paint: Paint) {
    drawCircle(color, r, center)
    drawCircle(Ink.Line, r, center, style = Stroke(max(1.5f, r * 0.14f)))
    label(text, Offset(center.x, center.y + r * 0.42f), r * 1.25f, Color.White, paint)
}

private fun DrawScope.label(text: String, baseline: Offset, size: Float, color: Color, paint: Paint, outline: Boolean = false) {
    drawIntoCanvas { c ->
        paint.textSize = size
        if (outline) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.16f
            paint.color = Ink.Paper.copy(alpha = color.alpha).toArgb()
            c.nativeCanvas.drawText(text, baseline.x, baseline.y, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = color.toArgb()
        c.nativeCanvas.drawText(text, baseline.x, baseline.y, paint)
    }
}
