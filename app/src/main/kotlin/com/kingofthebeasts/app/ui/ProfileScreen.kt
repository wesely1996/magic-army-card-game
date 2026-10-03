package com.kingofthebeasts.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kingofthebeasts.app.game.PaperDialog
import com.kingofthebeasts.app.settings.AppSettings
import com.kingofthebeasts.app.settings.MatchHistory
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.profile.MatchRecord
import com.kingofthebeasts.core.profile.MatchResult
import com.kingofthebeasts.core.profile.Tally
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** The player's picture: the King they've played most, or a plain crest before their first battle. */
@Composable
fun KingAvatar(kingId: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Ink.PaperDeep)
            .border(2.dp, Ink.Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (kingId != null) {
            Image(
                painterResource(CardArt.res(context, kingId)), CardDatabase.find(kingId)?.name,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        } else {
            Text("♛", style = MaterialTheme.typography.headlineMedium, color = Ink.Faded)
        }
    }
}

/** Name, picture, win rates and the match history. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val records = MatchHistory.records
    val stats = remember(records) { MatchHistory.stats }
    var confirmClear by remember { mutableStateOf(false) }
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("Profile", onBack)
            Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        KingAvatar(stats.favoriteKing, 84.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Your name", style = MaterialTheme.typography.labelLarge)
                            TextBox(AppSettings.playerName, { AppSettings.setName(it.take(24)) }, "Your name")
                            Text("Friends see this name in online battles.", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                        }
                    }
                    Text(
                        stats.kings.firstOrNull()?.let { (id, n) ->
                            "Your picture is your most played King: ${CardDatabase.find(id)?.name ?: id} ($n ${if (n == 1) "battle" else "battles"})."
                        } ?: "Your picture will be the King you play most.",
                        style = MaterialTheme.typography.bodySmall, color = Ink.Faded, modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("Record")
                    Text(
                        if (stats.overall.games == 0) "No battles yet — play one and it shows up here."
                        else "${stats.overall.games} battles · ${stats.overall.wins} won · ${stats.overall.losses} lost" +
                            (if (stats.overall.draws > 0) " · ${stats.overall.draws} drawn" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    for (level in Difficulty.entries) RateRow("vs ${level.displayName}", stats.vsAi.getValue(level))
                    RateRow("vs Friends", stats.vsFriends)
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("Favourites")
                    val race = stats.favoriteRace
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (race != null) RaceEmblem(race, 36.dp)
                        Text(
                            race?.let { "Favourite race: ${it.displayName}" } ?: "Favourite race: —",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "Favourite King: " + (stats.favoriteKing?.let { CardDatabase.find(it)?.name ?: it } ?: "—"),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                    if (stats.byRace.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        stats.byRace.entries.sortedByDescending { it.value.games }.forEach { (r, t) -> RateRow(r.displayName, t) }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionTitle("Match history") }
                        if (records.isNotEmpty()) SketchButton("Clear", { confirmClear = true }, small = true, color = Ink.PaperDeep)
                    }
                    if (records.isEmpty()) {
                        Text("Finished battles against the AI and your friends are listed here.", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(records.asReversed()) { MatchRow(it) }
                    }
                }
            }
        }
        if (confirmClear) {
            PaperDialog(onDismiss = { confirmClear = false }) {
                Text("Clear your match history?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Your win rates and favourites start again from zero. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Faded, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SketchButton("Keep it", { confirmClear = false }, color = Ink.Deploy)
                    SketchButton("Clear", { MatchHistory.clear(); confirmClear = false }, color = Ink.Enemy)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp).brushUnderline(Ink.Gold, text.length))
}

/** "vs Pro  3–2  60%" with a bar. */
@Composable
private fun RateRow(label: String, t: Tally) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(110.dp))
        val rate = t.winRate
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.PaperDeep)) {
            if (rate != null) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(rate.toFloat().coerceIn(0.02f, 1f)).background(Ink.Deploy))
            }
        }
        Text(
            if (rate == null) "—" else "${t.wins}–${t.losses}" + (if (t.draws > 0) "–${t.draws}" else "") + "  ${(rate * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.width(96.dp),
        )
    }
}

@Composable
private fun MatchRow(m: MatchRecord) {
    val (word, color) = when (m.result) {
        MatchResult.WIN -> "Victory" to Ink.You
        MatchResult.LOSS -> "Defeat" to Ink.Enemy
        MatchResult.DRAW -> "Draw" to Ink.Faded
    }
    val against = m.friend ?: m.difficulty?.let { d -> runCatching { Difficulty.valueOf(d).displayName }.getOrDefault(d) + " AI" } ?: "?"
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.Paper.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KingAvatar(m.myKing.ifEmpty { null }, 34.dp)
        Column(Modifier.weight(1f)) {
            Text(
                "$word vs $against" + if (m.forfeit) (if (m.result == MatchResult.WIN) " (they gave up)" else " (gave up)") else "",
                style = MaterialTheme.typography.labelLarge, color = color,
            )
            Text(
                "${kingName(m.myKing)} vs ${kingName(m.theirKing)} · ${m.turns} turns · " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.at)),
                style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
            )
        }
    }
}

private fun kingName(id: String) = CardDatabase.find(id)?.name ?: "?"
