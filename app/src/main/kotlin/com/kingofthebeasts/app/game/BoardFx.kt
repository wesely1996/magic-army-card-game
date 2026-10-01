package com.kingofthebeasts.app.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kingofthebeasts.app.audio.Sfx
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.game.Board
import com.kingofthebeasts.core.game.CastLook
import com.kingofthebeasts.core.model.CardType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Short board animations, timed in milliseconds from when they are queued. Positions are board
 * coordinates (square centres are x + 0.5, y + 0.5) so they follow the board when it turns.
 */
internal sealed class Fx(val delay: Long, val duration: Long) {
    /** Frame time the timeline started at; set on the first frame after the effect is queued. */
    var base = -1L
    private val start: Long get() = base + delay
    /** Progress from 0 to 1; 0 before the effect starts. */
    fun t(now: Long): Float = if (base < 0) 0f else ((now - start).toFloat() / duration).coerceIn(0f, 1f)
    fun started(now: Long): Boolean = base >= 0 && now >= start
    fun done(now: Long): Boolean = base >= 0 && now >= start + duration
}

/** A unit steps towards [dir] and back (melee strike, or a small recoil when shooting). */
internal class Lunge(delay: Long, val unitId: Int, val dir: Offset) : Fx(delay, 340)

/** A unit takes a hit: it shakes and flashes red. */
internal class Hit(delay: Long, val unitId: Int) : Fx(delay, 420)

/** A unit hops while sliding to a new square. */
internal class Hop(delay: Long, val unitId: Int) : Fx(delay, 380)

/** A unit drops onto the board; it stays invisible until the effect starts. */
internal class Appear(delay: Long, val unitId: Int) : Fx(delay, 460)

/** A defeated unit topples and fades; it stays standing until the effect starts. */
internal class Ghost(delay: Long, val cardId: String, val owner: Int, val at: Offset) : Fx(delay, 760)

/** A projectile: an arrow for ranged attacks, a glowing orb for spells and abilities. */
internal class Bolt(delay: Long, val from: Offset, val to: Offset, val color: Color, val arrow: Boolean) : Fx(delay, 340)

/** A ring and sparks on the ground, sized to the area of the effect. */
internal class Burst(delay: Long, val at: Offset, val color: Color, val radius: Float) : Fx(delay, 560)

/** A played card flies in from its owner's side of the board to where it takes effect. */
internal class CardFly(delay: Long, val cardId: String, val from: Offset, val to: Offset, val color: Color) : Fx(delay, 460)

/** A Strategy card washes the whole board in its colour. */
internal class FieldWave(delay: Long, val color: Color) : Fx(delay, 1000)

/** A sound effect timed with the animations; played once when its moment comes. */
internal class Cue(delay: Long, val sfx: Sfx, val volume: Float = 1f) : Fx(delay, 1) {
    var played = false
}

/** Floating text: damage, healing and status words. */
internal class FloatText(delay: Long, val text: String, val color: Color, val at: Offset, val row: Int = 0) : Fx(delay, 1400)

/** The sound of a spell or ability landing. */
internal fun lookSound(look: CastLook): Sfx? = when (look) {
    CastLook.HARM -> Sfx.SPELL_HARM
    CastLook.HELP -> Sfx.SPELL_HELP
    CastLook.CONTROL -> Sfx.SPELL_CONTROL
    CastLook.EQUIP -> Sfx.EQUIP
    CastLook.FIELD -> Sfx.FIELD
    CastLook.SUMMON -> null
}

internal fun lookColor(look: CastLook): Color = when (look) {
    CastLook.HARM -> Color(0xFFE0672B)
    CastLook.HELP -> Ink.Heal
    CastLook.CONTROL -> Ink.Target
    CastLook.SUMMON -> Ink.Deploy
    CastLook.EQUIP -> Ink.type(CardType.EQUIPMENT)
    CastLook.FIELD -> Ink.type(CardType.STRATEGY)
}

internal fun Offset.center(): Offset = Offset(x + 0.5f, y + 0.5f)

private fun ease(t: Float): Float = 1f - (1f - t) * (1f - t)

/** A point [height] squares above board point [p], on screen. */
private fun BoardProjection.above(p: Offset, height: Float): Offset =
    project(p.x, p.y) - Offset(0f, height * heightFactor * scale(p.x, p.y))

/** A circle of [r] squares around [c], lying on the board. */
private fun BoardProjection.groundCircle(c: Offset, r: Float): Path = Path().apply {
    for (i in 0..32) {
        val a = i / 32f * 2f * PI.toFloat()
        val p = project(c.x + r * cos(a), c.y + r * sin(a))
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

internal fun DrawScope.drawBolt(proj: BoardProjection, f: Bolt, now: Long) {
    if (!f.started(now)) return
    val t = f.t(now)
    val head = Offset(f.from.x + (f.to.x - f.from.x) * t, f.from.y + (f.to.y - f.from.y) * t)
    val s = proj.scale(head.x, head.y)
    // A gentle arc: higher in the middle of the flight.
    val h = 0.5f + 0.35f * sin(PI.toFloat() * t)
    val p = proj.above(head, h)
    if (f.arrow) {
        val back = Offset(head.x - (f.to.x - f.from.x) * 0.06f, head.y - (f.to.y - f.from.y) * 0.06f)
        val tail = proj.above(back, 0.5f + 0.35f * sin(PI.toFloat() * (t - 0.06f).coerceAtLeast(0f)))
        val angle = atan2(p.y - tail.y, p.x - tail.x)
        val len = 0.42f * s
        val start = Offset(p.x - cos(angle) * len, p.y - sin(angle) * len)
        drawLine(Ink.Line, start, p, strokeWidth = 0.045f * s, cap = StrokeCap.Round)
        val wing = 0.12f * s
        for (side in listOf(-1f, 1f)) {
            val a = angle + side * 2.6f
            drawLine(Ink.Line, p, Offset(p.x + cos(a) * wing, p.y + sin(a) * wing), strokeWidth = 0.04f * s, cap = StrokeCap.Round)
            val fa = angle + side * 0.5f
            drawLine(Ink.Faded, start, Offset(start.x - cos(fa) * wing, start.y - sin(fa) * wing), strokeWidth = 0.03f * s, cap = StrokeCap.Round)
        }
    } else {
        // trail of fading sparks behind a glowing orb
        for (i in 6 downTo 1) {
            val tt = (t - i * 0.045f).coerceAtLeast(0f)
            val q = Offset(f.from.x + (f.to.x - f.from.x) * tt, f.from.y + (f.to.y - f.from.y) * tt)
            val tp = proj.above(q, 0.5f + 0.35f * sin(PI.toFloat() * tt))
            drawCircle(f.color.copy(alpha = 0.5f * (1f - i / 7f)), 0.13f * s * (1f - i / 9f), tp)
        }
        drawCircle(Brush.radialGradient(listOf(f.color.copy(alpha = 0.55f), Color.Transparent), p, 0.36f * s), 0.36f * s, p)
        drawCircle(f.color, 0.15f * s, p)
        drawCircle(Color.White.copy(alpha = 0.85f), 0.07f * s, p)
    }
}

internal fun DrawScope.drawBurst(proj: BoardProjection, f: Burst, now: Long) {
    if (!f.started(now)) return
    val t = f.t(now)
    val s = proj.scale(f.at.x, f.at.y)
    val r = f.radius * (0.25f + 0.75f * ease(t))
    val fade = 1f - t
    drawPath(proj.groundCircle(f.at, r), f.color.copy(alpha = 0.22f * fade))
    drawPath(proj.groundCircle(f.at, r), f.color.copy(alpha = 0.9f * fade), style = Stroke(0.06f * s * (1f - 0.5f * t), join = StrokeJoin.Round))
    // sparks rising out of the ring
    for (i in 0 until 10) {
        val a = i / 10f * 2f * PI.toFloat() + i * 0.37f
        val rr = r * (0.55f + 0.45f * ((i * 7) % 5) / 4f)
        val base = Offset(f.at.x + rr * cos(a), f.at.y + rr * sin(a))
        val p = proj.above(base, 0.15f + 0.9f * ease(t) * (0.6f + 0.4f * ((i * 3) % 4) / 3f))
        drawCircle(f.color.copy(alpha = fade), 0.05f * s * (1f - 0.5f * t), p)
    }
    // a bright flash at the start
    if (t < 0.35f) {
        val c = proj.above(f.at, 0.45f)
        val k = 1f - t / 0.35f
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * k), f.color.copy(alpha = 0.4f * k), Color.Transparent), c, 0.6f * s), 0.6f * s, c)
    }
}

internal fun DrawScope.drawCardFly(proj: BoardProjection, f: CardFly, now: Long, art: ImageBitmap) {
    if (!f.started(now)) return
    val t = f.t(now)
    val e = ease(t)
    val at = Offset(f.from.x + (f.to.x - f.from.x) * e, f.from.y + (f.to.y - f.from.y) * e)
    val s = proj.scale(at.x, at.y)
    val c = proj.above(at, 0.9f + 0.7f * sin(PI.toFloat() * t))
    val k = if (t < 0.75f) 1f else 1f - (t - 0.75f) / 0.25f
    val w = 0.7f * s * (1.15f - 0.35f * t)
    val h = w * 1.4f
    val rect = Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
    rotate(-12f * (1f - t), c) {
        drawRect(Color.Black.copy(alpha = 0.25f * k), rect.topLeft + Offset(0.04f * s, 0.05f * s), rect.size)
        drawRect(f.color.copy(alpha = k), rect.topLeft, rect.size)
        val inner = Rect(rect.left + w * 0.08f, rect.top + w * 0.08f, rect.right - w * 0.08f, rect.bottom - h * 0.3f)
        val srcW = art.width
        val srcH = (art.width * inner.height / inner.width).roundToInt().coerceAtMost(art.height)
        drawImage(
            art, srcOffset = IntOffset(0, ((art.height - srcH) * 0.3f).roundToInt()), srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(inner.left.roundToInt(), inner.top.roundToInt()),
            dstSize = IntSize(inner.width.roundToInt(), inner.height.roundToInt()), alpha = k,
        )
        drawRect(Ink.Paper.copy(alpha = 0.85f * k), Offset(inner.left, inner.bottom + h * 0.04f), Size(inner.width, h * 0.18f))
        drawRect(Ink.Line.copy(alpha = k), rect.topLeft, rect.size, style = Stroke(0.025f * s))
    }
}

internal fun DrawScope.drawFieldWave(proj: BoardProjection, f: FieldWave, now: Long) {
    if (!f.started(now)) return
    val t = f.t(now)
    val n = Board.SIZE.toFloat()
    val quad = Path().apply {
        val a = proj.project(0f, 0f); val b = proj.project(n, 0f); val c = proj.project(n, n); val d = proj.project(0f, n)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    val glow = sin(PI.toFloat() * t)
    drawPath(quad, f.color.copy(alpha = 0.22f * glow))
    clipPath(quad) {
        val center = Offset(n / 2, n / 2)
        val r = 0.3f + 6.2f * ease(t)
        val s = proj.scale(center.x, center.y)
        drawPath(proj.groundCircle(center, r), f.color.copy(alpha = 0.85f * (1f - t)), style = Stroke(0.12f * s, join = StrokeJoin.Round))
        drawPath(proj.groundCircle(center, r * 0.7f), f.color.copy(alpha = 0.5f * (1f - t)), style = Stroke(0.06f * s, join = StrokeJoin.Round))
    }
}

internal fun DrawScope.drawFloatText(proj: BoardProjection, f: FloatText, now: Long, label: DrawScope.(String, Offset, Float, Color) -> Unit) {
    if (!f.started(now)) return
    val t = f.t(now)
    val s = proj.scale(f.at.x, f.at.y)
    // pops a little larger, then drifts up and fades
    val pop = if (t < 0.12f) 0.7f + 2.5f * t else 1f
    // several words on one unit stack upwards instead of overlapping
    val at = proj.project(f.at.x, f.at.y) - Offset(0f, (0.75f + 0.32f * f.row + t * 0.6f) * s)
    label(f.text, at, 0.3f * s * pop, f.color.copy(alpha = (1f - t * t).coerceIn(0f, 1f)))
}

/** Unit-level animation state at [now]: how to offset, scale and tint one standee. */
internal class UnitPose(
    /** Offset in board squares (lunges). */
    val boardShift: Offset = Offset.Zero,
    /** Sideways shake in squares. */
    val shake: Float = 0f,
    /** Height above the board in squares (hops, drops). */
    val lift: Float = 0f,
    val scale: Float = 1f,
    /** Red hit flash strength, 0 to 1. */
    val flash: Float = 0f,
    val hidden: Boolean = false,
)

internal fun poseOf(unitId: Int, fx: List<Fx>, now: Long): UnitPose {
    var shift = Offset.Zero
    var shake = 0f
    var lift = 0f
    var scale = 1f
    var flash = 0f
    var hidden = false
    for (f in fx) when (f) {
        is Lunge -> if (f.unitId == unitId && f.started(now)) {
            val k = sin(PI.toFloat() * f.t(now))
            shift += f.dir * k
        }
        is Hit -> if (f.unitId == unitId && f.started(now)) {
            val t = f.t(now)
            shake += sin(t * PI.toFloat() * 7f) * (1f - t) * 0.07f
            flash = maxOf(flash, (1f - t) * 0.6f)
        }
        is Hop -> if (f.unitId == unitId && f.started(now)) lift += 0.18f * sin(PI.toFloat() * f.t(now))
        is Appear -> if (f.unitId == unitId) {
            if (!f.started(now)) hidden = true
            else {
                val t = f.t(now)
                // drop in from above and settle with a small bounce
                lift += 0.8f * (1f - ease(minOf(1f, t / 0.55f)))
                scale *= if (t < 0.55f) 0.6f + 0.4f * t / 0.55f else 1f + 0.12f * sin(PI.toFloat() * (t - 0.55f) / 0.45f)
            }
        }
        else -> {}
    }
    return UnitPose(shift, shake, lift, scale, flash, hidden)
}

internal fun Offset.normalized(len: Float): Offset {
    val d = sqrt(x * x + y * y)
    return if (d == 0f) Offset.Zero else Offset(x / d * len, y / d * len)
}
