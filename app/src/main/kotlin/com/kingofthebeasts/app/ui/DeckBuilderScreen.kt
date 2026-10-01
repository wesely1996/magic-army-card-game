package com.kingofthebeasts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.Race

/**
 * Pick up to three races, then add cards from their pools. The deck can be
 * saved as a draft at any time; only valid decks can be taken into battle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckBuilderScreen(
    initial: Deck?,
    existingNames: Set<String>,
    onBack: () -> Unit,
    onSave: (Deck) -> Unit,
) {
    var deck by remember { mutableStateOf(initial ?: Deck("", emptyList(), emptyMap())) }
    var filter by remember { mutableStateOf<CardType?>(null) }
    var preview by remember { mutableStateOf<CardDef?>(null) }
    val errors = DeckRules.validate(deck)
    val nameTaken = deck.name.isNotBlank() && deck.name != initial?.name && deck.name in existingNames

    fun toggleRace(r: Race) {
        deck = if (r in deck.races) {
            deck.copy(races = deck.races - r, cards = deck.cards.filterKeys { CardDatabase.get(it).race != r })
        } else if (deck.races.size < DeckRules.MAX_RACES) {
            deck.copy(races = deck.races + r)
        } else deck
    }

    val pool = CardDatabase.all
        .filter { it.race in deck.races && (filter == null || it.type == filter) }
        .sortedWith(compareBy<CardDef>({ !it.isKing }, { it.type }, { it.race }, { it.name }))

    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader(if (initial == null) "New Deck" else "Edit Deck", onBack) {
                SketchButton(
                    "Save", { onSave(deck.copy(name = deck.name.trim())) },
                    small = true, color = Ink.Deploy, enabled = deck.name.isNotBlank() && !nameTaken,
                )
            }
            Row(Modifier.weight(1f)) {
                // Left: name, races, filters and the deck summary. Right: the card pool.
                Column(
                    Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Ink.Paper.copy(alpha = 0.7f))
                            .sketchBorder(seed = 31)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (deck.name.isEmpty()) Text("Name your deck…", style = MaterialTheme.typography.bodyLarge, color = Ink.Faded)
                        BasicTextField(
                            deck.name, { deck = deck.copy(name = it.take(28)) },
                            textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (nameTaken) Text("You already have a deck with this name.", style = MaterialTheme.typography.bodySmall, color = Ink.Enemy)
                    Spacer(Modifier.height(8.dp))
                    Text("Races (${deck.races.size}/${DeckRules.MAX_RACES})", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Race.entries.forEach { r ->
                            val on = r in deck.races
                            Row(
                                Modifier
                                    .then(if (on) Modifier.watercolor(Ink.race(r), r.ordinal, 1.3f) else Modifier)
                                    .sketchBorder(if (on) Ink.Line else Ink.Faded, seed = r.ordinal)
                                    .clickable { toggleRace(r) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RaceEmblem(r, 26.dp, on)
                                Spacer(Modifier.width(4.dp))
                                Text(r.displayName, style = MaterialTheme.typography.labelMedium, color = if (on) Ink.Line else Ink.Faded)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf<CardType?>(null) + CardType.entries).forEach { t ->
                            val on = filter == t
                            Text(
                                t?.displayName ?: "All",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .then(if (on) Modifier.watercolor(t?.let { Ink.type(it) } ?: Ink.Gold, 40 + (t?.ordinal ?: 9), 1.3f) else Modifier)
                                    .sketchBorder(if (on) Ink.Line else Ink.Faded, seed = 50 + (t?.ordinal ?: 9))
                                    .clickable { filter = t }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val king = deck.cards.keys.map { CardDatabase.get(it) }.filter { it.isKing }
                    Text(
                        "Cards ${deck.mainSize}/${DeckRules.DECK_SIZE} + King: ${king.joinToString { it.name }.ifEmpty { "none yet" }}  ·  " +
                            "Strategy ${deck.strategyCount}/${DeckRules.MAX_STRATEGY}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    king.firstOrNull()?.race?.trait?.let { t ->
                        Text("Racial trait — ${t.displayName}: ${t.description}", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                    }
                    if (errors.isEmpty()) {
                        Text("✓ Ready for battle", style = MaterialTheme.typography.bodySmall, color = Ink.Heal)
                    } else {
                        errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Ink.Enemy) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (deck.races.isEmpty()) {
                        Text(
                            "Pick up to three races to see their cards.",
                            style = MaterialTheme.typography.bodyLarge, color = Ink.Faded,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                    LazyVerticalGrid(
                        GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pool, key = { it.id }) { def ->
                            val count = deck.cards[def.id] ?: 0
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CardFace(
                                    def, width = 104.dp, badge = if (count > 0) "×$count" else null, selected = count > 0,
                                    onClick = { preview = def }, onLongClick = { preview = def },
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SketchButton("−", { deck = deck.withCount(def.id, count - 1) }, small = true, color = Ink.Enemy, enabled = count > 0)
                                    Text("$count/${def.maxCopies}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
                                    SketchButton(
                                        "+", { deck = deck.withCount(def.id, count + 1) }, small = true, color = Ink.Deploy,
                                        enabled = count < def.maxCopies && when {
                                            def.isKing -> deck.cards.keys.none { CardDatabase.get(it).isKing }
                                            def.type == CardType.STRATEGY -> deck.mainSize < DeckRules.DECK_SIZE && deck.strategyCount < DeckRules.MAX_STRATEGY
                                            else -> deck.mainSize < DeckRules.DECK_SIZE
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        preview?.let { CardInspectDialog(it, onDismiss = { preview = null }) }
    }
}
