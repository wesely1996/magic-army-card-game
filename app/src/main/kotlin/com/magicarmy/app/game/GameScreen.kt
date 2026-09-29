package com.magicarmy.app.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.magicarmy.app.ui.CardFace
import com.magicarmy.app.ui.SketchButton
import com.magicarmy.app.ui.sketchBorder
import com.magicarmy.app.ui.theme.Ink
import com.magicarmy.app.ui.watercolor
import com.magicarmy.core.game.Action
import com.magicarmy.core.game.DecisionKind
import com.magicarmy.core.game.GameEngine
import com.magicarmy.core.game.GameEvent
import com.magicarmy.core.game.Phase
import com.magicarmy.core.game.UnitState
import com.magicarmy.core.model.CardDef
import kotlinx.coroutines.delay

@Composable
fun GameScreen(vm: GameViewModel, onExit: () -> Unit, onRematch: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val version = vm.version // recompose on every state change
    val s = vm.state
    val actions = vm.legalActions()
    var detail by remember { mutableStateOf<CardDef?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler { confirmExit = true }

    // Big announcements (coin flip, battle start) fade in over the board.
    var announcement by remember { mutableStateOf<String?>(null) }
    var announcedSeq by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.version) {
        val next = s.events.filterIsInstance<GameEvent.Announce>().lastOrNull { it.seq > announcedSeq } ?: return@LaunchedEffect
        announcedSeq = next.seq
        announcement = next.text
        delay(1800)
        announcement = null
    }

    Box(Modifier.fillMaxSize().background(Ink.Paper)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(vm, onMenu = { confirmExit = true }, onLog = { showLog = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BoardView(vm, vm.highlights(actions), Modifier.fillMaxSize())
                androidx.compose.animation.AnimatedVisibility(
                    announcement != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut(),
                ) {
                    Text(
                        announcement ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Ink.Paper.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                            .watercolor(Ink.Gold, 3, 1.4f)
                            .sketchBorder(seed = 4)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
            PromptRow(vm, actions)
            if (s.stack.isNotEmpty()) ChainPanel(vm)
            SelectionPanel(vm, actions, onInspect = { detail = it })
            HandRow(vm, actions, onInspect = { detail = it })
        }

        if (s.phase == Phase.GAME_OVER) GameOverDialog(vm, onExit, onRematch)
        detail?.let { def -> CardDetailDialog(def) { detail = null } }
        if (showLog) LogDialog(s.log) { showLog = false }
        if (confirmExit) {
            PaperDialog(onDismiss = { confirmExit = false }) {
                Text("Leave the battle?", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SketchButton("Stay", { confirmExit = false }, color = Ink.Move)
                    SketchButton("Leave", onExit, color = Ink.Enemy)
                }
            }
        }
    }
}

@Composable
private fun TopBar(vm: GameViewModel, onMenu: () -> Unit, onLog: () -> Unit) {
    val s = vm.state
    val opp = s.players[1 - vm.human]
    val me = s.players[vm.human]
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SketchButton("☰", onMenu, small = true, color = Ink.PaperDeep)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Opponent  ✋${opp.hand.size}  🂠${opp.deck.size}",
                style = MaterialTheme.typography.labelMedium, color = Ink.Enemy,
            )
            Text(
                "You  🂠${me.deck.size}  ·  " + when (s.phase) {
                    Phase.DEPLOY -> "Deployment"
                    Phase.BATTLE -> "Turn ${s.turnNumber}"
                    Phase.GAME_OVER -> "Battle over"
                },
                style = MaterialTheme.typography.labelMedium, color = Ink.You,
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (f in s.fields) {
                Text(
                    "⚑ ${f.rule.displayName} ${f.turns}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .watercolor(if (f.owner == vm.human) Ink.You else Ink.Enemy, f.rule.ordinal, 1.3f)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        SketchButton("Log", onLog, small = true, color = Ink.PaperDeep)
    }
}

@Composable
private fun PromptRow(vm: GameViewModel, actions: List<Action>) {
    val s = vm.state
    val d = vm.decision
    val me = s.players[vm.human]
    val text = when {
        s.phase == Phase.GAME_OVER -> "The battle is over."
        vm.aiThinking || d.player != vm.human -> "Opponent is thinking…"
        d.kind == DecisionKind.DEPLOY && me.deployed == 0 -> "Place your King in your first 3 rows."
        d.kind == DecisionKind.DEPLOY -> "Deploy units (${me.deployed}/${GameEngine.MAX_DEPLOY}) — pick a card, then a square."
        d.kind == DecisionKind.RESPOND -> "Interrupt “${s.stack.lastOrNull()?.label}”? Use a Magic card or ⚡ ability, or pass."
        s.blitzUsed -> "Blitz! Take one more action."
        else -> "Your turn: move, attack, use an ability or play a card."
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        when {
            d.kind == DecisionKind.DEPLOY && Action.EndDeploy in actions ->
                SketchButton("Done", { vm.perform(Action.EndDeploy) }, small = true, color = Ink.Deploy)
            d.kind == DecisionKind.RESPOND && vm.humanToAct ->
                SketchButton("Pass", { vm.perform(Action.Pass) }, small = true, color = Ink.Gold)
            d.kind == DecisionKind.MAIN && vm.humanToAct ->
                SketchButton("Skip turn", { vm.perform(Action.Pass) }, small = true, color = Ink.PaperDeep)
        }
    }
}

@Composable
private fun ChainPanel(vm: GameViewModel) {
    val s = vm.state
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .watercolor(Ink.Target, 21, 0.9f)
            .sketchBorder(seed = 22)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("⚡ Chain (resolves from the top)", style = MaterialTheme.typography.labelSmall)
        s.stack.asReversed().forEach { item ->
            Text(
                "${s.players[item.controller].name}: ${item.label}",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.controller == vm.human) Ink.You else Ink.Enemy,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectionPanel(vm: GameViewModel, actions: List<Action>, onInspect: (CardDef) -> Unit) {
    val s = vm.state
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 10.dp, vertical = 2.dp)) {
        when (val sel = vm.selection) {
            is Selection.Unit -> s.unit(sel.unitId)?.let { UnitInfo(vm, it, actions, onInspect) }
            is Selection.Card -> {
                val card = (s.players[vm.human].hand + s.players[vm.human].deck).firstOrNull { it.uid == sel.cardUid }
                if (card != null) {
                    val confirm = vm.confirmableActions(actions)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(card.def.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (confirm.isEmpty()) "Choose a highlighted square." else card.def.text,
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        confirm.firstOrNull()?.let { SketchButton("Play", { vm.perform(it) }, small = true, color = Ink.Target) }
                    }
                }
            }
            is Selection.Ability -> s.unit(sel.unitId)?.let { u ->
                val ab = u.abilities[sel.index].def
                Column {
                    Text("${u.name}: ${ab.name}", style = MaterialTheme.typography.titleMedium)
                    Text("${ab.text} Choose a highlighted target.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Selection.None -> Text(
                s.log.lastOrNull() ?: "",
                style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
                modifier = Modifier.align(Alignment.CenterStart), maxLines = 3,
            )
        }
    }
}

@Composable
private fun UnitInfo(vm: GameViewModel, u: UnitState, actions: List<Action>, onInspect: (CardDef) -> Unit) {
    val s = vm.state
    val mine = u.owner == vm.human
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (u.isKing) "♛ " else "") + u.name,
                style = MaterialTheme.typography.titleMedium, color = if (mine) Ink.You else Ink.Enemy,
                modifier = Modifier.clickable { onInspect(u.def) },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "⚔${GameEngine.attackOf(s, u)}  ♥${u.hp}/${u.maxHp}  👣${GameEngine.moveOf(s, u)}  🎯${GameEngine.rangeOf(s, u)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        val traits = buildList {
            u.keywords.forEach { add(it.displayName) }
            u.timedKeywords.forEach { add("${it.keyword.displayName} (${it.turns})") }
            if (u.stun > 0) add("Stunned ${u.stun}")
            if (u.poisonTurns > 0) add("Poison ${u.poisonDamage}×${u.poisonTurns}")
            if (u.shield > 0) add("Shield ${u.shield}")
            u.equipment.forEach { add("⛨ $it") }
        }
        if (traits.isNotEmpty()) Text(traits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        if (u.abilities.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                u.abilities.forEachIndexed { i, a ->
                    val usable = mine && vm.abilityActions(u.id, i, actions).isNotEmpty()
                    val label = a.def.name + (if (a.def.quick) " ⚡" else "") + (if (a.cooldown > 0) " (${a.cooldown})" else "")
                    SketchButton(label, { vm.selectAbility(u.id, i) }, small = true, color = Ink.Target, enabled = usable)
                }
                Text(u.abilities.first().def.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HandRow(vm: GameViewModel, actions: List<Action>, onInspect: (CardDef) -> Unit) {
    val cards = vm.handCards()
    val deploying = vm.state.phase == Phase.DEPLOY
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (deploying) "Deployment pool — tap a unit, then a blue square. Long-press to read."
            else "Your hand (${cards.size}) — long-press a card to read it.",
            style = MaterialTheme.typography.labelSmall, color = Ink.Faded,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LazyRow(
            Modifier.fillMaxWidth().height(128.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(cards, key = { it.first.uid }) { (card, count) ->
                val playable = vm.cardActions(card.uid, actions).isNotEmpty()
                CardFace(
                    card.def,
                    width = 86.dp,
                    selected = vm.selection == Selection.Card(card.uid),
                    dimmed = vm.humanToAct && !playable,
                    badge = if (count > 1) "×$count" else null,
                    onClick = { if (playable) vm.selectCard(card.uid) else onInspect(card.def) },
                    onLongClick = { onInspect(card.def) },
                )
            }
        }
    }
}

@Composable
fun PaperDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Ink.Paper, RoundedCornerShape(18.dp))
                .watercolor(Ink.Gold, 77, 0.6f)
                .sketchBorder(seed = 78, corner = 18.dp)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
fun CardDetailDialog(def: CardDef, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CardFace(def, width = 290.dp, onClick = onDismiss)
    }
}

@Composable
private fun LogDialog(log: List<String>, onDismiss: () -> Unit) {
    PaperDialog(onDismiss) {
        Text("Battle log", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.heightIn(max = 420.dp).fillMaxWidth()) {
            items(log.asReversed()) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (line.startsWith("—")) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SketchButton("Close", onDismiss, small = true, color = Ink.PaperDeep)
    }
}

@Composable
private fun GameOverDialog(vm: GameViewModel, onExit: () -> Unit, onRematch: () -> Unit) {
    val s = vm.state
    val (title, color) = when {
        s.isDraw -> "Draw" to Ink.Gold
        s.winner == vm.human -> "Victory!" to Ink.You
        else -> "Defeat" to Ink.Enemy
    }
    PaperDialog(onDismiss = {}) {
        Text(title, style = MaterialTheme.typography.displayLarge, color = color)
        Text(s.log.lastOrNull { "King" in it || "draw" in it } ?: "", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SketchButton("Rematch", onRematch, color = Ink.Deploy)
            SketchButton("Menu", onExit, color = Ink.PaperDeep)
        }
    }
}
