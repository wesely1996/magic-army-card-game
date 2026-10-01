package com.kingofthebeasts.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.kingofthebeasts.app.ui.theme.BrushFont
import com.kingofthebeasts.app.ui.theme.HandFont
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.EffectOp

const val CARD_ASPECT = 5f / 7f

/** Short rules text for small cards, full text for large ones. */
fun cardText(def: CardDef, full: Boolean): String {
    val u = def.unit ?: return def.rulesText
    if (full) return def.text
    return buildList {
        u.keywords.forEach { add(it.displayName) }
        u.abilities.forEach { add(it.name + if (it.quick) " ⚡" else "") }
        // e.g. "Arrival: +2 Swarm Rat"
        u.arrival.filterIsInstance<EffectOp.Summon>().groupBy { it.cardId }.forEach { (id, list) ->
            add("Arrival: +${list.size} ${CardDatabase.get(id).name}")
        }
    }.joinToString(" · ").ifEmpty { if (u.isKing) "King" else "" }
}

fun typeSymbol(t: CardType) = when (t) {
    CardType.UNIT -> "⚔"
    CardType.MAGIC -> "✦"
    CardType.STRATEGY -> "⚑"
    CardType.EQUIPMENT -> "⛨"
}

/**
 * A card: full-bleed watercolor art, name banner on top, base stats in their
 * own fields and the rules text in a box at the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardFace(
    def: CardDef,
    modifier: Modifier = Modifier,
    width: Dp = 100.dp,
    full: Boolean = width >= 200.dp,
    selected: Boolean = false,
    dimmed: Boolean = false,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val seed = def.id.hashCode()
    val shape = RoundedCornerShape(width * 0.07f)
    BoxWithConstraints(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT)
            .alpha(if (dimmed) 0.45f else 1f)
            .then(if (selected) Modifier.border(3.dp, Ink.Gold, shape) else Modifier)
            .clip(shape)
            .background(Ink.Paper)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                } else Modifier,
            ),
    ) {
        val w = maxWidth
        val unit = with(LocalDensity.current) { (w * 0.1f).toSp() }
        val raceColor = Ink.race(def.race)

        Image(
            painterResource(CardArt.res(context, def.id)), contentDescription = def.name,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
        )

        Column(Modifier.fillMaxSize()) {
            // Name banner
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(w * 0.04f)
                    .watercolor(raceColor, seed, 1.4f)
                    .sketchBorder(Ink.Line, w * 0.012f, w * 0.05f, seed)
                    .padding(horizontal = w * 0.05f, vertical = w * 0.01f),
            ) {
                Text(
                    def.name,
                    style = TextStyle(fontFamily = BrushFont, fontSize = unit * 1.15f, color = Ink.Line, lineHeight = 1.05.em),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart).padding(end = w * 0.14f),
                )
                Text(
                    typeSymbol(def.type),
                    style = TextStyle(fontFamily = HandFont, fontSize = unit * 1.0f, color = Ink.type(def.type), fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            if (def.isKing || def.unit?.isElite == true) {
                Text(
                    if (def.isKing) "♛ KING" else "★ ELITE",
                    style = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = unit * 0.7f, color = Ink.Line),
                    modifier = Modifier
                        .padding(start = w * 0.05f)
                        .watercolor(Ink.Gold, seed + 1, 1.6f)
                        .padding(horizontal = w * 0.04f),
                )
            }
            Box(Modifier.weight(1f))

            // Base stats, each in its own field
            def.unit?.let { st ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = w * 0.04f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatField("ATK", st.attack, Ink.Attack, w, seed + 2)
                    StatField("HP", st.health, Ink.Heal, w, seed + 3)
                    StatField("MOV", st.move, Ink.Move, w, seed + 4)
                    StatField("RNG", st.range, Ink.Gold, w, seed + 5)
                }
            }

            // Rules text box
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (full) 1.55f else 2.3f)
                    .padding(w * 0.04f)
                    .background(Ink.Paper.copy(alpha = 0.88f), RoundedCornerShape(w * 0.04f))
                    .sketchBorder(Ink.Line, w * 0.01f, w * 0.04f, seed + 6)
                    .padding(horizontal = w * 0.05f, vertical = w * 0.025f),
            ) {
                Column {
                    Text(
                        "${def.race.displayName} · ${def.type.displayName}",
                        style = TextStyle(fontFamily = HandFont, fontSize = unit * 0.55f, color = Ink.Faded, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    val body = cardText(def, full)
                    // Large cards shrink long rules text so all of it fits in the box.
                    val bodySize = when {
                        !full -> 0.68f
                        body.length < 120 -> 0.52f
                        body.length < 190 -> 0.46f
                        else -> 0.40f
                    }
                    Text(
                        body,
                        style = TextStyle(fontFamily = HandFont, fontSize = unit * bodySize, color = Ink.Line, lineHeight = 1.12.em),
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (full && def.flavor.isNotEmpty()) {
                        Text(
                            "“${def.flavor}”",
                            style = TextStyle(fontFamily = HandFont, fontSize = unit * 0.48f, color = Ink.Faded),
                            modifier = Modifier.padding(top = w * 0.015f),
                        )
                    }
                }
            }
        }

        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = w * 0.2f, end = w * 0.04f)
                    .background(Ink.Line, CircleShape)
                    .padding(horizontal = w * 0.05f, vertical = w * 0.01f),
            ) {
                Text(badge, style = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = unit * 0.75f, color = Ink.Paper))
            }
        }
    }
}

@Composable
private fun StatField(label: String, value: Int, color: Color, w: Dp, seed: Int) {
    val unit = with(LocalDensity.current) { (w * 0.1f).toSp() }
    Column(
        Modifier
            .width(w * 0.21f)
            .background(Ink.Paper.copy(alpha = 0.85f), RoundedCornerShape(w * 0.04f))
            .watercolor(color, seed, 1.1f)
            .sketchBorder(Ink.Line, w * 0.008f, w * 0.04f, seed),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value.toString(),
            style = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = unit * 1.0f, color = Ink.Line, lineHeight = 1.0.em),
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            style = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = unit * 0.45f, color = Ink.Line, lineHeight = 1.0.em),
        )
    }
}
