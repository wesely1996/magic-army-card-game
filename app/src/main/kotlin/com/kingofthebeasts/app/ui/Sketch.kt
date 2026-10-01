package com.kingofthebeasts.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kingofthebeasts.app.R
import com.kingofthebeasts.app.audio.GameAudio
import com.kingofthebeasts.app.audio.Sfx
import com.kingofthebeasts.app.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hand-drawn look: wobbly ink outlines drawn twice (like a quick pen sketch)
 * and translucent watercolor washes with darker pooled edges.
 */
object Sketch {
    /** A rounded rectangle whose outline wobbles slightly, as if drawn by hand. */
    fun wobblyRect(size: Size, corner: Float, jitter: Float, rnd: Random, inset: Float = 0f): Path {
        val path = Path()
        val l = inset; val t = inset; val r = size.width - inset; val b = size.height - inset
        val c = min(corner, min(r - l, b - t) / 2)
        val pts = mutableListOf<Offset>()
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float) {
            val len = kotlin.math.hypot(x1 - x0, y1 - y0)
            val n = (len / 18f).toInt().coerceAtLeast(2)
            for (i in 0 until n) {
                val f = i / n.toFloat()
                pts += Offset(x0 + (x1 - x0) * f + rnd.range(-jitter, jitter), y0 + (y1 - y0) * f + rnd.range(-jitter, jitter))
            }
        }
        fun arc(cx: Float, cy: Float, from: Double) {
            for (i in 0..3) {
                val a = from + i * (PI / 2) / 4
                pts += Offset(cx + (c * cos(a)).toFloat(), cy + (c * sin(a)).toFloat())
            }
        }
        edge(l + c, t, r - c, t); arc(r - c, t + c, -PI / 2)
        edge(r, t + c, r, b - c); arc(r - c, b - c, 0.0)
        edge(r - c, b, l + c, b); arc(l + c, b - c, PI / 2)
        edge(l, b - c, l, t + c); arc(l + c, t + c, PI)
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1..pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i % pts.size]
            path.quadraticTo(p0.x, p0.y, (p0.x + p1.x) / 2, (p0.y + p1.y) / 2)
        }
        path.close()
        return path
    }

    /** An irregular blob, used for watercolor washes. */
    fun blob(center: Offset, rx: Float, ry: Float, rnd: Random, roughness: Float = 0.18f): Path {
        val n = 22
        val phase = rnd.nextFloat() * 6f
        val path = Path()
        val pts = (0 until n).map { i ->
            val a = i * 2 * PI / n
            val k = 1f + roughness * (sin(a * 3 + phase).toFloat() * 0.5f + rnd.range(-0.6f, 0.6f))
            Offset(center.x + (rx * k * cos(a)).toFloat(), center.y + (ry * k * sin(a)).toFloat())
        }
        path.moveTo((pts[0].x + pts[1].x) / 2, (pts[0].y + pts[1].y) / 2)
        for (i in 1..n) {
            val p = pts[i % n]
            val q = pts[(i + 1) % n]
            path.quadraticTo(p.x, p.y, (p.x + q.x) / 2, (p.y + q.y) / 2)
        }
        path.close()
        return path
    }

    fun DrawScope.washBlob(path: Path, color: Color, alpha: Float) {
        drawPath(path, color.copy(alpha = alpha))
        drawPath(path, color.copy(alpha = alpha * 0.9f), style = Stroke(width = 2.5f))
    }
}

/** Double-stroked, wobbly hand-drawn border. */
fun Modifier.sketchBorder(
    color: Color = Ink.Line,
    width: Dp = 1.6.dp,
    corner: Dp = 10.dp,
    seed: Int = 0,
): Modifier = drawWithCache {
    val rnd = Random(seed)
    val w = width.toPx()
    val a = Sketch.wobblyRect(size, corner.toPx(), 1.3f, rnd, inset = w)
    val b = Sketch.wobblyRect(size, corner.toPx(), 1.8f, rnd, inset = w * 1.4f)
    onDrawWithContent {
        drawContent()
        drawPath(a, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(b, color.copy(alpha = 0.35f), style = Stroke(w * 0.6f, cap = StrokeCap.Round))
    }
}

/** A loose watercolor wash that fills (and slightly spills) inside the component. */
fun Modifier.watercolor(color: Color, seed: Int = 0, strength: Float = 1f): Modifier = drawWithCache {
    val rnd = Random(seed)
    val blobs = List(3) {
        Sketch.blob(
            Offset(size.width * rnd.range(0.3f, 0.7f), size.height * rnd.range(0.3f, 0.7f)),
            size.width * rnd.range(0.45f, 0.6f), size.height * rnd.range(0.5f, 0.7f), rnd,
        )
    }
    val base = Sketch.wobblyRect(size, 14f, 2.5f, rnd, inset = 2f)
    onDrawBehind {
        drawPath(base, color.copy(alpha = 0.22f * strength))
        blobs.forEachIndexed { i, p -> with(Sketch) { washBlob(p, color, (0.16f + i * 0.05f) * strength) } }
    }
}

@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.paper_background), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
        content()
    }
}

@Composable
fun SketchButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink.You,
    enabled: Boolean = true,
    seed: Int = text.hashCode(),
    small: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .watercolor(color, seed, 1.3f)
            .sketchBorder(Ink.Line, 1.6.dp, 12.dp, seed)
            .clickable(interaction, indication = null, enabled = enabled) {
                GameAudio.play(if (text == "←") Sfx.BACK else Sfx.CLICK)
                onClick()
            }
            .padding(horizontal = if (small) 12.dp else 20.dp, vertical = if (small) 6.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Underline drawn with a brush-like wobble, used under headings. */
fun Modifier.brushUnderline(color: Color, seed: Int = 1): Modifier = drawBehind {
    val rnd = Random(seed)
    val y = size.height - 3f
    val path = Path().apply {
        moveTo(0f, y + rnd.range(-2f, 2f))
        quadraticTo(size.width * 0.5f, y + rnd.range(-5f, 5f), size.width, y + rnd.range(-2f, 2f))
    }
    drawPath(path, color.copy(alpha = 0.55f), style = Stroke(7f, cap = StrokeCap.Round))
}

private fun Random.range(from: Float, until: Float): Float = from + nextFloat() * (until - from)
