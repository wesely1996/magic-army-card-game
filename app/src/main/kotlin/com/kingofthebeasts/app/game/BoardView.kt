package com.kingofthebeasts.app.game

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.res.ResourcesCompat
import com.kingofthebeasts.app.R
import com.kingofthebeasts.app.ui.CardArt
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.game.Board
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
import kotlinx.coroutines.launch

/**
 * Perspective projection of the 8×8 board plane, seen by a camera that can orbit the
 * board's centre. Board coordinates: x to the right, y away from the human player (row 0 is
 * nearest at angle 0). [angleDeg] turns the board around its centre; the camera always looks
 * from the bottom of the screen, so 180° shows the board from the opponent's side.
 */
class BoardProjection(width: Float, height: Float, angleDeg: Float = 0f) {
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

    val slab: Float get() = 0.25f * b / z0

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
        val z = z0 + v + half
        return Offset(cx + u * b / z, horizonY + a / z)
    }

    /** Screen height of a [len]-square step away from the camera at board point ([bx], [by]). */
    fun depthSpan(bx: Float, by: Float, len: Float): Float {
        val z = z0 + depth(bx, by) + half
        return a * len / (z * z)
    }

    /** Screen pixels per board square at board point ([bx], [by]). */
    fun scale(bx: Float, by: Float): Float = b / (z0 + depth(bx, by) + half)

    /** The board square under a screen point (as fractional board coordinates), or null above the horizon. */
    fun unproject(o: Offset): Offset? {
        val dy = o.y - horizonY
        if (dy <= 0f) return null
        val z = a / dy
        val u = (o.x - cx) * z / b
        val v = z - z0 - half
        return Offset(u * cos + v * sin + Board.SIZE / 2f, -u * sin + v * cos + Board.SIZE / 2f)
    }

    /** Whether a board edge with outward normal (nx, ny) faces the camera. */
    fun facesCamera(nx: Float, ny: Float): Boolean = nx * sin + ny * cos < -0.01f

    fun quad(x: Int, y: Int, inset: Float = 0f): Path = Path().apply {
        val p0 = project(x + inset, y + inset)
        val p1 = project(x + 1 - inset, y + inset)
        val p2 = project(x + 1 - inset, y + 1 - inset)
        val p3 = project(x + inset, y + 1 - inset)
        moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
    }
}

private class Popup(val text: String, val color: Color, val at: Offset, val anim: Animatable<Float, *>)

private class HitBox(val rect: Rect, val pos: Pos)

@Composable
fun BoardView(
    vm: GameViewModel,
    highlights: Highlights,
    modifier: Modifier = Modifier,
    angle: Float = 0f,
    onRotate: (Float) -> Unit = {},
    onInspect: (UnitState) -> Unit = {},
) {
    val state = vm.state
    val version = vm.version
    val context = LocalContext.current
    val boardTexture = ImageBitmap.imageResource(R.drawable.board_texture)
    val arts = remember { HashMap<String, ImageBitmap>() }
    fun art(id: String) = arts.getOrPut(id) { ImageBitmap.imageResource(context.resources, CardArt.res(context, id)) }
    val handFace = remember { ResourcesCompat.getFont(context, R.font.kalam_bold) ?: Typeface.DEFAULT_BOLD }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = handFace; textAlign = Paint.Align.CENTER } }
    val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val matrix = remember { Matrix() }
    val projHolder = remember { arrayOfNulls<BoardProjection>(1) }
    val hitBoxes = remember { mutableListOf<HitBox>() }
    val currentHighlights = rememberUpdatedState(highlights)

    val positions = remember { HashMap<Int, Animatable<Offset, AnimationVector2D>>() }
    val popups = remember { mutableStateListOf<Popup>() }
    var lastSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(version) {
        val alive = state.units.map { it.id }.toSet()
        positions.keys.retainAll(alive)
        for (u in state.units) {
            val target = Offset(u.pos.x + 0.5f, u.pos.y + 0.5f)
            val anim = positions[u.id]
            if (anim == null) positions[u.id] = Animatable(target, Offset.VectorConverter)
            else if (anim.targetValue != target) launch { anim.animateTo(target, tween(380)) }
        }
        var delayIndex = 0
        for (e in state.events) {
            if (e.seq <= lastSeq) continue
            val (text, color, pos) = when (e) {
                is GameEvent.Damaged -> Triple(if (e.amount > 0) "-${e.amount}" else "0", Ink.Attack, e.pos)
                is GameEvent.Healed -> Triple("+${e.amount}", Ink.Heal, e.pos)
                is GameEvent.Died -> Triple("✖", Ink.Line, e.pos)
                is GameEvent.Status -> Triple(e.text, Ink.Target, e.pos)
                else -> continue
            }
            val popup = Popup(text, color, Offset(pos.x + 0.5f, pos.y + 0.5f), Animatable(0f))
            popups += popup
            val startDelay = delayIndex++ * 180
            launch {
                popup.anim.animateTo(1f, tween(1400, delayMillis = startDelay, easing = LinearEasing))
                popups.remove(popup)
            }
        }
        lastSeq = state.eventSeq
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
        val proj = BoardProjection(size.width, size.height, angle)
        projHolder[0] = proj

        drawBoardBase(proj, boardTexture, matrix, bitmapPaint)
        drawZones(proj, state.phase == Phase.DEPLOY)
        drawHighlights(proj, highlights)
        drawCoordinates(proj, textPaint)

        // Units, far to near, so nearer standees overlap farther ones.
        hitBoxes.clear()
        val drawn = state.units.filter { it.alive }.map { u ->
            u to (positions[u.id]?.value ?: Offset(u.pos.x + 0.5f, u.pos.y + 0.5f))
        }.sortedByDescending { proj.depth(it.second.x, it.second.y) }
        val boxes = mutableListOf<HitBox>()
        for ((u, bp) in drawn) {
            val s = proj.scale(bp.x, bp.y)
            val base = proj.project(bp.x, bp.y)
            val team = if (u.owner == vm.human) Ink.You else Ink.Enemy
            // Apparent height of the base disc: the screen height of a 0.68-square step in depth.
            val depth = proj.depthSpan(bp.x, bp.y, 0.68f)

            drawOval(Color.Black.copy(alpha = 0.22f), Offset(base.x - 0.4f * s, base.y - depth / 2 + 0.03f * s), Size(0.8f * s, depth))
            drawOval(team.copy(alpha = 0.55f), Offset(base.x - 0.33f * s, base.y - depth * 0.42f), Size(0.66f * s, depth * 0.84f))
            drawOval(Ink.Line.copy(alpha = 0.8f), Offset(base.x - 0.33f * s, base.y - depth * 0.42f), Size(0.66f * s, depth * 0.84f), style = Stroke(0.02f * s))
            if (u.shield > 0) {
                drawOval(Ink.Move.copy(alpha = 0.8f), Offset(base.x - 0.42f * s, base.y - depth * 0.55f), Size(0.84f * s, depth * 1.1f), style = Stroke(0.05f * s))
            }

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
        // nearest first for hit testing
        hitBoxes += boxes.asReversed()

        for (p in popups) {
            val t = p.anim.value
            if (t <= 0f) continue
            val s = proj.scale(p.at.x, p.at.y)
            val at = proj.project(p.at.x, p.at.y) - Offset(0f, (0.75f + t * 0.6f) * s)
            label(p.text, at, 0.3f * s, p.color.copy(alpha = (1f - t * t).coerceIn(0f, 1f)), textPaint, outline = true)
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

private fun DrawScope.drawZones(proj: BoardProjection, deploy: Boolean) {
    if (!deploy) return
    for (y in 0 until Board.SIZE) for (x in 0 until Board.SIZE) {
        val c = when {
            Board.isDeployZone(0, Pos(x, y)) -> Ink.You.copy(alpha = 0.13f)
            Board.isDeployZone(1, Pos(x, y)) -> Ink.Enemy.copy(alpha = 0.10f)
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
        label(('a' + x).toString(), Offset(p.x, p.y + 0.1f * proj.scale(x + 0.5f, -0.3f)), 0.24f * proj.scale(x + 0.5f, -0.3f), Ink.Faded, paint)
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
