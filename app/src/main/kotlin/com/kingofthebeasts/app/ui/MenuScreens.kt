package com.kingofthebeasts.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.model.Race

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SketchButton("←", onBack, small = true, color = Ink.PaperDeep)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).brushUnderline(Ink.Gold))
        trailing()
    }
}

@Composable
fun RaceEmblem(race: Race, size: androidx.compose.ui.unit.Dp = 44.dp, selected: Boolean = true) {
    val context = LocalContext.current
    val king = CardDatabase.ofRace(race).first { it.isKing }
    Image(
        painterResource(CardArt.res(context, king.id)), contentDescription = race.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Ink.race(race) else Ink.Faded, CircleShape),
    )
}

@Composable
fun MenuScreen(onPlay: () -> Unit, onDecks: () -> Unit, onRules: () -> Unit) {
    PaperBackground {
        Row(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("King of the", style = MaterialTheme.typography.headlineMedium, color = Ink.Faded)
                Text(
                    "Beasts",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    modifier = Modifier.brushUnderline(Ink.Gold, 3),
                )
                Text("card chess of the wild clans", style = MaterialTheme.typography.bodyLarge, color = Ink.Faded)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Race.entries.forEach { RaceEmblem(it, 52.dp) } }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                SketchButton("⚔  Battle", onPlay, Modifier.width(240.dp), color = Ink.Enemy)
                Spacer(Modifier.height(14.dp))
                SketchButton("🂠  Deck Builder", onDecks, Modifier.width(240.dp), color = Ink.You)
                Spacer(Modifier.height(14.dp))
                SketchButton("📜  How to Play", onRules, Modifier.width(240.dp), color = Ink.Gold)
                Spacer(Modifier.height(28.dp))
                Text(
                    "Online battles with friends — coming soon",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
                )
                Text(
                    "Illustrations built from game-icons.net (CC BY 3.0)",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
                )
            }
        }
    }
}

@Composable
fun DeckListScreen(
    decks: List<Deck>,
    onBack: () -> Unit,
    onEdit: (Deck?) -> Unit,
    onDelete: (Deck) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<Deck?>(null) }
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("Your Decks", onBack) { SketchButton("+ New", { onEdit(null) }, small = true, color = Ink.Deploy) }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (decks.isEmpty()) item {
                    Text(
                        "No decks yet. Build one, or copy a starter deck below to tweak it.",
                        style = MaterialTheme.typography.bodyMedium, color = Ink.Faded, modifier = Modifier.padding(8.dp),
                    )
                }
                items(decks, key = { "mine-" + it.name }) { d ->
                    DeckRow(d) {
                        SketchButton("Edit", { onEdit(d) }, small = true, color = Ink.You)
                        Spacer(Modifier.width(6.dp))
                        SketchButton("✕", { confirmDelete = d }, small = true, color = Ink.Enemy)
                    }
                }
                item { Text("Starter decks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
                items(StarterDecks.all, key = { "starter-" + it.name }) { d ->
                    DeckRow(d) { SketchButton("Copy", { onEdit(d.copy(name = d.name + " (copy)")) }, small = true, color = Ink.Gold) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        confirmDelete?.let { d ->
            com.kingofthebeasts.app.game.PaperDialog(onDismiss = { confirmDelete = null }) {
                Text("Delete “${d.name}”?", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SketchButton("Keep", { confirmDelete = null }, color = Ink.Move)
                    SketchButton("Delete", { onDelete(d); confirmDelete = null }, color = Ink.Enemy)
                }
            }
        }
    }
}

@Composable
fun DeckRow(deck: Deck, selected: Boolean = false, onClick: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val valid = DeckRules.isValid(deck)
    val king = deck.cards.keys.mapNotNull { CardDatabase.find(it) }.firstOrNull { it.isKing }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.watercolor(Ink.Gold, deck.name.hashCode(), 1.2f) else Modifier)
            .sketchBorder(if (selected) Ink.Line else Ink.Faded, seed = deck.name.hashCode())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { deck.races.forEach { RaceEmblem(it, 36.dp) } }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(deck.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${deck.size}/${DeckRules.DECK_SIZE} · ${king?.name ?: "no King"} · " + if (valid) "ready ✓" else "draft",
                style = MaterialTheme.typography.bodySmall, color = if (valid) Ink.Heal else Ink.Enemy,
            )
        }
        actions()
    }
}

@Composable
fun PlaySetupScreen(decks: List<Deck>, onBack: () -> Unit, onStart: (Deck, Deck?, Difficulty) -> Unit) {
    val playable = decks.filter { DeckRules.isValid(it) } + StarterDecks.all
    var mine by remember { mutableStateOf(playable.first()) }
    var opponent by remember { mutableStateOf<Deck?>(null) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("Battle", onBack)
            Row(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("Your army", style = MaterialTheme.typography.titleLarge)
                    playable.forEach { d ->
                        Box(Modifier.padding(vertical = 4.dp)) { DeckRow(d, selected = d == mine, onClick = { mine = d }) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Difficulty", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Difficulty.entries.forEach { d ->
                                val on = d == difficulty
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                        .then(if (on) Modifier.watercolor(if (d == Difficulty.EASY) Ink.Heal else Ink.Enemy, 60 + d.ordinal, 1.2f) else Modifier)
                                        .sketchBorder(if (on) Ink.Line else Ink.Faded, seed = 60 + d.ordinal)
                                        .clickable { difficulty = d }
                                        .padding(10.dp),
                                ) {
                                    Text(d.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(d.description, style = MaterialTheme.typography.bodySmall, color = if (on) Ink.Line else Ink.Faded)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Opponent", style = MaterialTheme.typography.titleLarge)
                        Box(Modifier.padding(vertical = 4.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (opponent == null) Modifier.watercolor(Ink.Gold, 9, 1.2f) else Modifier)
                                    .sketchBorder(if (opponent == null) Ink.Line else Ink.Faded, seed = 9)
                                    .clickable { opponent = null }
                                    .padding(14.dp),
                            ) { Text("🎲  Random starter deck", style = MaterialTheme.typography.titleMedium) }
                        }
                        StarterDecks.all.forEach { d ->
                            Box(Modifier.padding(vertical = 4.dp)) { DeckRow(d, selected = d == opponent, onClick = { opponent = d }) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        SketchButton("⚔  To battle!", { onStart(mine, opponent, difficulty) }, Modifier.width(240.dp), color = Ink.Enemy)
                    }
                }
            }
        }
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val sections = listOf(
        "Goal" to "Defeat the enemy King. If your King falls, you lose.",
        "Decks" to "Build a 40-card deck from up to 3 animal races. It must contain exactly one King. At most 3 copies of any other card.",
        "Card types" to "Units put a piece on the board. Magic cards buff, heal, damage or cancel — and can be played as interrupts. " +
            "Strategy cards change the rules of the battlefield for a few turns. Equipment permanently upgrades one of your units.",
        "Displacement spells" to "Each race has one Magic card that moves or swaps units. Pack Relay (Wolf) sends an ally back " +
            "into your deck and a random unit from your deck takes its square. Mighty Shove (Bear) pushes an enemy 2 squares away " +
            "from your nearest unit, with 2 damage if something stops it. Gale Force (Hawk) blows an enemy up to 3 squares back toward " +
            "its own side. Mirage (Serpent) sends an enemy back into its owner's deck and a random unit from that deck takes its square. " +
            "Royal Exchange (Lion) lets an ally and your King trade squares — handy to pull the King out of danger.",
        "Deployment" to "A coin flip decides who starts. Players take turns placing one unit at a time in their first 3 rows, " +
            "up to 5 units each. Your King must be the first unit you place. You choose from all unit cards in your deck. " +
            "Afterwards everyone shuffles and draws 5 cards.",
        "Battle" to "On your turn draw a card, then take ONE action: move a unit, attack with a unit, use a unit's ability, " +
            "or play a card. Units move up to their MOV in any direction (8 ways), and can't pass through other units unless they fly. " +
            "They attack enemies within RNG squares (diagonals count). Boosts \"for 1 turn\" played on your own turn last " +
            "through your next turn, so the unit gets to use them.",
        "Exhaustion" to "From turn 120 on, each King loses health at the start of its owner's turn (1, rising by 1 every 20 turns) " +
            "and can no longer be healed, so every battle reaches an ending.",
        "Reinforcements" to "During the battle, unit cards are played on an empty square at the edge of the board " +
            "that is at least 2 squares away from every enemy. Each side can have at most 10 units on the board " +
            "(summoned and enthralled units count too).",
        "Interrupts" to "Every action can be answered. When your opponent acts, you may respond with a Magic card or a ⚡ quick ability — " +
            "and they may respond to that, and so on. Then everything resolves from the last response back to the first. " +
            "An action that no longer makes sense (a stunned unit's attack, a target that died) fizzles.",
        "Inspecting" to "Press and hold any card — in your hand, in the deck builder or a unit on the board — to open it large " +
            "with every rule, trait and ability explained. Units on the board also show their current stats and effects.",
        "Controls" to "When it's your move your hand fans out in front of the board. Pick a card and the hand moves to the side " +
            "while you choose a highlighted square. Swipe the hand down or tap Hide to look at the board, and tap the strip on the " +
            "left (or swipe it right) to bring it back. ⟲ and ⟳ turn the board, a two-finger twist turns it freely, and " +
            "Reset view puts your side back at the bottom. The rail on the right shows the action queue; tap Details " +
            "or swipe it left for a full explanation of what's going on.",
        "Opponents" to "Easy plays on instinct and sometimes slips. Medium thinks 3 moves ahead: its move, your best reply, and its follow-up.",
        "Kings" to "Every race has two Kings with a signature trick: the Alpha Wolf calls a pup at the start of each of your turns " +
            "(up to 2), the Moon Howler feeds on every kill, " +
            "the Elder Bear can't be stunned or hit for more than 3, the Cave Warden guards nearby allies, the Sky Sovereign swaps " +
            "places with allies, the Storm Eagle strikes nearby enemies with lightning, the Naga Queen enthralls weak enemies, the Basilisk petrifies " +
            "what it bites, the Pride King inspires allies around him and the Lioness Queen pounces across the board.",
    )
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("How to Play", onBack)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                sections.forEachIndexed { i, (title, body) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .watercolor(listOf(Ink.You, Ink.Gold, Ink.Heal, Ink.Target, Ink.Enemy)[i % 5], i, 0.7f)
                            .sketchBorder(seed = i)
                            .padding(12.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
