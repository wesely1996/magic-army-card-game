package com.kingofthebeasts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType

/**
 * A deck shown read-only (the starter decks): every card with its count, the King and its racial
 * trait. Tap or press and hold a card to inspect it; Copy makes an editable copy.
 */
@Composable
fun DeckViewScreen(deck: Deck, onBack: () -> Unit, onCopy: () -> Unit) {
    var preview by remember { mutableStateOf<CardDef?>(null) }
    val cards = deck.cards.mapNotNull { (id, n) -> CardDatabase.find(id)?.let { it to n } }
        .sortedWith(compareBy<Pair<CardDef, Int>>({ !it.first.isKing }, { it.first.type }, { -it.first.stars }, { it.first.name }))
    val king = cards.firstOrNull { it.first.isKing }?.first
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader(deck.name, onBack) {
                SketchButton("Copy to edit", onCopy, small = true, color = Ink.Gold)
            }
            Row(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { deck.races.forEach { RaceEmblem(it, 34.dp) } }
                    Text("Starter deck — view only", style = MaterialTheme.typography.labelMedium, color = Ink.Faded)
                    Text(
                        "${deck.mainSize}/${DeckRules.DECK_SIZE} cards + King: ${king?.name ?: "none"}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    king?.race?.trait?.let { t ->
                        Text("Racial trait — ${t.displayName}: ${t.description}", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                    }
                    Spacer(Modifier.height(6.dp))
                    for (type in CardType.entries) {
                        val n = cards.filter { it.first.type == type && !it.first.isKing }.sumOf { it.second }
                        if (n > 0) Text("${type.displayName}: $n", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Tap a card to read it.", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(cards, key = { it.first.id }) { (def, count) ->
                            CardFace(
                                def, width = 104.dp, badge = "×$count",
                                onClick = { preview = def }, onLongClick = { preview = def },
                            )
                        }
                    }
                }
            }
        }
        preview?.let { CardInspectDialog(it, onDismiss = { preview = null }) }
    }
}
