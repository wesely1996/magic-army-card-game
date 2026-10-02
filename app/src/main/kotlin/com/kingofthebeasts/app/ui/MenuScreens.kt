package com.kingofthebeasts.app.ui

import androidx.activity.compose.BackHandler
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
fun MenuScreen(
    onPlay: () -> Unit,
    onDecks: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit = {},
    /** A short description of the saved battle in progress, if any (e.g. "Turn 14 vs Venom & Wings"). */
    resumeLabel: String? = null,
    onResume: () -> Unit = {},
    /** Online play with a friend; null while it isn't available. */
    onFriends: (() -> Unit)? = null,
    /** Whether the play options behind "Play" are showing instead of the basic menu. */
    playMenu: Boolean = false,
    onPlayMenu: (Boolean) -> Unit = {},
    onTutorial: () -> Unit = {},
    /** Shown under With friends, e.g. an unfinished online battle. */
    friendsLabel: String? = null,
    /** Offer the tutorial once, on first launch. */
    offerTutorial: Boolean = false,
    onTutorialOffered: () -> Unit = {},
) {
    var confirmNew by remember { mutableStateOf(false) }
    BackHandler(enabled = playMenu) { onPlayMenu(false) }
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
                val wide = Modifier.width(240.dp)
                if (!playMenu) {
                    SketchButton("⚔  Play", { onPlayMenu(true) }, wide, color = Ink.Enemy)
                    Spacer(Modifier.height(14.dp))
                    SketchButton("🂠  Deck Builder", onDecks, wide, color = Ink.You)
                    Spacer(Modifier.height(14.dp))
                    SketchButton("📜  How to Play", onRules, wide, color = Ink.Gold)
                } else {
                    SketchButton("▶  Continue", onResume, wide, color = Ink.Deploy, enabled = resumeLabel != null)
                    Text(resumeLabel ?: "No battle in progress", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                    Spacer(Modifier.height(10.dp))
                    SketchButton("⚔  New game", { if (resumeLabel != null) confirmNew = true else onPlay() }, wide, color = Ink.Enemy)
                    Spacer(Modifier.height(14.dp))
                    SketchButton("🎓  Tutorial", onTutorial, wide, color = Ink.Gold)
                    Spacer(Modifier.height(14.dp))
                    SketchButton("🤝  With friends", { onFriends?.invoke() }, wide, color = Ink.You, enabled = onFriends != null)
                    Text(friendsLabel ?: if (onFriends == null) "Online play — coming soon" else "Online, on the same Wi-Fi", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                    Spacer(Modifier.height(14.dp))
                    SketchButton("←  Back", { onPlayMenu(false) }, wide, color = Ink.PaperDeep)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Illustrations built from game-icons.net (CC BY 3.0) · music and sounds CC0 (see Settings)",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Faded, textAlign = TextAlign.Center,
                )
            }
        }
        // The settings wheel sits in the top corner.
        Box(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp)) {
            SketchButton("⚙", onSettings, color = Ink.PaperDeep)
        }
        if (offerTutorial) {
            com.kingofthebeasts.app.game.PaperDialog(onDismiss = onTutorialOffered) {
                Text("New to King of the Beasts?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "A short guided battle teaches deploying, moving, attacking, answering interrupts and playing cards.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SketchButton("🎓  Play the tutorial", { onTutorialOffered(); onTutorial() }, color = Ink.Gold)
                    SketchButton("Not now", onTutorialOffered, color = Ink.PaperDeep)
                }
                Text("You'll find it later under Play and in How to Play.", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
            }
        }
        if (confirmNew) {
            com.kingofthebeasts.app.game.PaperDialog(onDismiss = { confirmNew = false }) {
                Text("You have a battle in progress", style = MaterialTheme.typography.titleLarge)
                Text("Starting a new battle ends it for good.", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SketchButton("Continue it", { confirmNew = false; onResume() }, color = Ink.Deploy)
                    SketchButton("New game", { confirmNew = false; onPlay() }, color = Ink.Enemy)
                }
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
    /** Opens a starter deck read-only. */
    onView: (Deck) -> Unit = {},
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
                    DeckRow(d, onClick = { onEdit(d) }) {
                        SketchButton("Edit", { onEdit(d) }, small = true, color = Ink.You)
                        Spacer(Modifier.width(6.dp))
                        SketchButton("✕", { confirmDelete = d }, small = true, color = Ink.Enemy)
                    }
                }
                item { Text("Starter decks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
                items(StarterDecks.all, key = { "starter-" + it.name }) { d ->
                    DeckRow(d, onClick = { onView(d) }) {
                        SketchButton("View", { onView(d) }, small = true, color = Ink.You)
                        Spacer(Modifier.width(6.dp))
                        SketchButton("Copy", { onEdit(d.copy(name = d.name + " (copy)")) }, small = true, color = Ink.Gold)
                    }
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
                "${deck.mainSize}/${DeckRules.DECK_SIZE} + ${king?.name ?: "no King"} · " + if (valid) "ready ✓" else "draft",
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
                                        .then(if (on) Modifier.watercolor(
                                            when (d) {
                                                Difficulty.EASY -> Ink.Heal
                                                Difficulty.MEDIUM -> Ink.Enemy
                                                Difficulty.HARD -> Ink.Target
                                            },
                                            60 + d.ordinal, 1.2f,
                                        ) else Modifier)
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
fun RulesScreen(onBack: () -> Unit, onTutorial: () -> Unit = {}) {
    val sections = listOf(
        "Goal" to "Defeat the enemy King. If your King falls, you lose. New here? The Tutorial battle (top right) walks " +
            "you through a first battle step by step.",
        "Decks" to "Build a deck of 40 cards plus one King, from up to 3 of the 6 races. Copies per card follow its stars: " +
            "★ cards up to 3, ★★ up to 2, ★★★ just 1. At most 3 Strategy cards and at most 3 Champions — so choose them well.",
        "Racial traits" to "The race of your King gives your whole army a trait. Wolf — Pack Tactics: all your units have Pack Hunter " +
            "and +1 movement. Bear — Thick Fur: +1 health, but units with 3+ movement get −1 movement. Hawk — Eagle Eyes: ranged " +
            "units get +1 range. Serpent — Venom Blood: all units are Poisonous, but units with 3+ attack get −1 attack. " +
            "Lion — Royal Pride: your King gets +1 health and +1 attack. " +
            "Vermin — Endless Horde: 22 unit slots instead of 16.",
        "Kings are special" to "Kings take no damage from Magic cards or abilities — only attacks (and Exhaustion) can bring them " +
            "down. They are Immovable: nothing can push, swap or replace them. And they don't take a unit slot.",
        "Used cards" to "Unit cards, equipment, Strategy cards and spells that deal damage or summon units are exhausted: once used " +
            "they are out of the game. Other Magic cards go to your discard pile; when your deck runs out, the discard " +
            "pile is shuffled into a new deck.",
        "Card types" to "Units put a piece on the board: ★ normal units, ★★ Elite units and ★★★ Champions. Magic cards (★ to ★★★) " +
            "buff, heal, damage, move or cancel — and can be played as interrupts. Strategy cards are powerful fields: only " +
            "one field is on the battlefield at a time, and it stays until any Strategy card replaces it. Equipment " +
            "permanently upgrades one of your units.",
        "Displacement spells" to "Each race has one Magic card that moves or swaps units. Pack Relay (Wolf) sends an ally back " +
            "into your deck and a random unit from your deck takes its square. Mighty Shove (Bear) pushes an enemy 2 squares away " +
            "from your nearest unit, with 2 damage if something stops it. Gale Force (Hawk) blows an enemy up to 3 squares back toward " +
            "its own side. Mirage (Serpent) sends an enemy back into its owner's deck and a random unit from that deck takes its square. " +
            "Rally to the King (Lion) brings an ally next to your King. Rat Run (Vermin) swaps an enemy with your nearest unit.",
        "Keywords" to "Hidden: can only be attacked or targeted from a square next to it. Backstab: +2 attack when attacking " +
            "from behind (from the target's own side of the board). Brood: a Swarm Rat pops out next to it at the start of your " +
            "next 2 turns. Arrival: happens when the unit is played. Immovable: can't be pushed, swapped or replaced. " +
            "Elite (★★): takes 2 unit slots. Champion (★★★): takes 3.",
        "Deployment" to "A coin flip decides who starts. Players take turns placing one unit at a time in their first 3 rows, " +
            "up to 6 units each. Your King must be the first unit you place. You choose from all unit cards in your deck. " +
            "Afterwards everyone shuffles and draws 5 cards.",
        "Battle" to "On your turn draw a card (you can hold up to 10), then take ONE action: move a unit, attack with a unit, " +
            "use a unit's ability, or play a card. Kings and Champions that fight in melee (range 1) may attack right after " +
            "they move, in the same turn; every other unit moves or attacks. Quick spells don't use your action at all. " +
            "Kings are tough (around 30 health) but mostly weak fighters: protect yours with a wall of units. " +
            "Units move up to their MOV in any direction (8 ways), and can't pass through other units unless they fly. " +
            "They attack enemies within RNG squares (diagonals count). Boosts \"for 1 turn\" played on your own turn last " +
            "through your next turn, so the unit gets to use them.",
        "Exhaustion" to "From turn 120 on, each King loses health at the start of its owner's turn (1, rising by 1 every 20 turns) " +
            "and can no longer be healed, so every battle reaches an ending.",
        "Reinforcements" to "During the battle, unit cards are played on an empty square at the edge of the board " +
            "that is at least 2 squares away from every enemy. Each side has 16 unit slots (22 with the Endless Horde): " +
            "Champions take 3 slots, Elite units 2, other units 1 and the King none. Summoned and enthralled units count too.",
        "Quick spells" to "Spells marked QUICK are a little weaker but free: playing one on your turn doesn't use up your action, " +
            "so you can still move, attack or play another card. Like all Magic, they also work as interrupts.",
        "Structures" to "Structures (★ STRUCTURE) are unit cards that never move or attack by themselves and can't be pushed. " +
            "Sentry structures fire at the weakest enemy in range at the start of your turn. Taunt structures are sturdy: an enemy " +
            "next to one can only attack the structure (or move away first). Mending Aura structures heal your units next to them by 2 each turn.",
        "Evolution" to "Units marked EVOLVES grow stronger: after surviving a number of your turns, or after defeating enemies, " +
            "they turn into their evolved form (some evolve twice). Evolving heals the unit fully and keeps its equipment. " +
            "Evolved forms can't be put in decks.",
        "Clearing the field" to "Every race has a spell that ends the Strategy field on the battlefield, whoever played it, " +
            "and a spell that draws 2 cards.",
        "Turn timer" to "In online battles each decision has 20 seconds. When your time runs out you pass (during deployment a " +
            "unit is placed for you). Battles against the computer have no timer.",
        "Interrupts" to "Every action can be answered. When your opponent acts, you may respond with a Magic card or a ⚡ interrupt ability — " +
            "and they may respond to that, and so on. Then everything resolves from the last response back to the first. " +
            "An action that no longer makes sense (a stunned unit's attack, a target that died) fizzles.",
        "Inspecting" to "Press and hold any card — in your hand, in the deck builder or a unit on the board — to open it large " +
            "with every rule, trait and ability explained. Units on the board also show their current stats and effects.",
        "Controls" to "When it's your move your hand fans out in front of the board. Pick a card and the hand moves to the side " +
            "while you choose a highlighted square. Swipe the hand down or tap Hide to look at the board, and tap the strip on the " +
            "left (or swipe it right) to bring it back. ⟲ and ⟳ turn the board, a two-finger twist turns it freely, and " +
            "Reset view puts your side back at the bottom. The rail on the right shows the action queue; tap Details " +
            "or swipe it left for a full explanation of what's going on.",
        "Menu" to "Play opens the play options: Continue picks up your battle in progress, New game starts one against " +
            "the AI, and With friends plays online. The ⚙ wheel in the top corner opens Settings: music and sound " +
            "volume, animation speed, keeping the screen on, and the credits.",
        "Continue" to "Your battle is saved after every move. If you leave it (or close the app), Play → Continue " +
            "picks it up. Forfeit ends it for good, and starting a new game replaces it.",
        "With friends" to "Play a friend on the same Wi-Fi. Pick your name and army, then one of you taps Host a game " +
            "and the other Join a game: hosted games show up in a list, or join by the address the host's screen shows. " +
            "You both need the same version of the game. Each phone runs the battle and only moves are sent, checked " +
            "against each other after every action. If the connection drops the battle waits and reconnects by itself. " +
            "Leave for now keeps it saved: tap Rejoin on both phones under With friends to carry on, even after closing the app. " +
            "Forfeit hands your friend the win, and after a battle you can both tap Rematch.",
        "Battle log" to "Tap Details (or swipe the rail left) for the drawer: it covers most of the screen and explains " +
            "what is going on, the action queue, both armies, the field and recent events. Full battle log shows everything.",
        "Opponents" to "Beginner plays on instinct: half the time it takes its best-looking move, otherwise one of the next few. " +
            "Pro thinks 2 moves ahead: its move and your best reply. Master thinks as far ahead as about a second and a half allows, and looks ahead before " +
            "deciding whether to interrupt you. None of them peek at your hand.",
        "Kings" to "Every race has two Kings with a signature trick: the Alpha Wolf calls a pup at the start of each of your turns " +
            "(up to 2), the Moon Howler feeds on every kill, " +
            "the Elder Bear can't be stunned or hit for more than 3, the Cave Warden guards nearby allies, the Sky Sovereign swaps " +
            "places with allies, the Storm Eagle strikes nearby enemies with lightning, the Naga Queen enthralls weak enemies, the Basilisk petrifies " +
            "what it bites, the Pride King inspires allies around him, the Lioness Queen pounces across the board, the Rat King " +
            "calls rats to his side and the Blight Seer hides while his blight bolts hurt everything around the target.",
    )
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("How to Play", onBack) {
                SketchButton("🎓  Tutorial battle", onTutorial, small = true, color = Ink.Gold)
            }
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
