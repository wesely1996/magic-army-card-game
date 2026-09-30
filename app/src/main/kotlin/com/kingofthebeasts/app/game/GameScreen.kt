package com.kingofthebeasts.app.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.kingofthebeasts.app.ui.CARD_ASPECT
import com.kingofthebeasts.app.ui.CardFace
import com.kingofthebeasts.app.ui.CardInspectDialog
import com.kingofthebeasts.app.ui.SketchButton
import com.kingofthebeasts.app.ui.sketchBorder
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.app.ui.watercolor
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.CardInstance
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameEvent
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.UnitState
import com.kingofthebeasts.core.model.CardDef
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameScreen(vm: GameViewModel, onExit: () -> Unit, onRematch: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val version = vm.version // recompose on every state change
    val s = vm.state
    val actions = vm.legalActions()
    val scope = rememberCoroutineScope()
    // Card being inspected; unitId is set when it was opened from a unit on the board.
    var detail by remember { mutableStateOf<Pair<CardDef, Int?>?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler { confirmExit = true }

    // Board rotation in degrees (buttons turn it in 90° steps, a two-finger twist freely).
    val angle = remember { Animatable(0f) }
    fun rotateBy(step: Float) = scope.launch {
        val base = (angle.value / 90f).roundToInt() * 90f
        angle.animateTo(base + step, tween(450))
    }

    // The hand comes to the front whenever it's your move and tucks away to the side otherwise.
    var handExpanded by remember { mutableStateOf(false) }
    val humanToAct = vm.humanToAct
    LaunchedEffect(humanToAct, s.phase) {
        // When answering the opponent, only bother bringing the hand up if a card can be played.
        val decision = vm.decision
        handExpanded = humanToAct && (
            decision.kind != DecisionKind.RESPOND ||
                vm.handCards().any { (c, _) -> vm.cardActions(c.uid, actions).isNotEmpty() }
            )
    }
    // Docked cards sit in a strip on the left; nudge the board over so they don't cover it.
    val hasCards = vm.handCards().isNotEmpty()
    val boardInset by animateDpAsState(if (!handExpanded && hasCards) 84.dp else 0.dp, tween(350), label = "boardInset")
    // Picking where to play (a card that needs a square, a unit or an ability) moves the cards aside.
    LaunchedEffect(vm.selection) {
        val sel = vm.selection
        if (sel is Selection.Unit || sel is Selection.Ability) handExpanded = false
        if (sel is Selection.Card && vm.confirmableActions(vm.legalActions()).isEmpty()) handExpanded = false
    }

    // Big announcements (coin flip, battle start) fade in over the board.
    var announcement by remember { mutableStateOf<GameEvent.Announce?>(null) }
    var announcedSeq by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.version) {
        val next = s.events.filterIsInstance<GameEvent.Announce>().lastOrNull { it.seq > announcedSeq } ?: return@LaunchedEffect
        announcedSeq = next.seq
        announcement = next
    }
    // The hide timer is keyed on the announcement itself, not on the game version: the AI
    // keeps changing the game while the banner is up, and that must not cancel the timer.
    LaunchedEffect(announcement) {
        if (announcement == null) return@LaunchedEffect
        delay(1800)
        announcement = null
    }

    Box(Modifier.fillMaxSize().background(Ink.Paper)) {
        Row(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                BoardView(
                    vm, vm.highlights(actions), Modifier.fillMaxSize().padding(start = boardInset),
                    angle = angle.value,
                    onRotate = { delta -> scope.launch { angle.snapTo(angle.value + delta) } },
                    onInspect = { u -> detail = u.def to u.id },
                )
                BoardControls(
                    rotated = (((angle.value % 360f) + 360f) % 360f).let { it > 1f && it < 359f },
                    onMenu = { confirmExit = true },
                    onRotateLeft = { rotateBy(-90f) },
                    onRotateRight = { rotateBy(90f) },
                    onReset = { scope.launch { angle.animateTo((angle.value / 360f).roundToInt() * 360f, tween(450)) } },
                    modifier = Modifier.align(Alignment.TopStart),
                )
                androidx.compose.animation.AnimatedVisibility(
                    announcement != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut(),
                ) {
                    // Keep showing the last text while the banner fades out.
                    val text = remember { mutableStateOf("") }
                    announcement?.let { text.value = it.text }
                    Text(
                        text.value,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Ink.Paper.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                            .watercolor(Ink.Gold, 3, 1.4f)
                            .sketchBorder(seed = 4)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
                HandOverlay(
                    vm, actions,
                    expanded = handExpanded,
                    onExpandedChange = { handExpanded = it },
                    onInspect = { detail = it to null },
                    modifier = Modifier.matchParentSize(),
                )
            }
            SidePanel(vm, actions, onLog = { showLog = true }, onInspect = { u -> detail = u.def to u.id })
        }

        if (s.phase == Phase.GAME_OVER) GameOverDialog(vm, onExit, onRematch)
        detail?.let { (def, unitId) ->
            CardInspectDialog(def, onDismiss = { detail = null }, unit = unitId?.let { s.unit(it) }, state = s)
        }
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
private fun BoardControls(
    rotated: Boolean,
    onMenu: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        SketchButton("☰", onMenu, small = true, color = Ink.PaperDeep)
        SketchButton("⟲", onRotateLeft, small = true, color = Ink.PaperDeep)
        SketchButton("⟳", onRotateRight, small = true, color = Ink.PaperDeep)
        if (rotated) SketchButton("Reset view", onReset, small = true, color = Ink.Gold)
    }
}

/** Right-hand column: both armies' status, what to do now, the interrupt chain and the selection. */
@Composable
private fun SidePanel(vm: GameViewModel, actions: List<Action>, onLog: () -> Unit, onInspect: (UnitState) -> Unit) {
    val s = vm.state
    val opp = s.players[1 - vm.human]
    val me = s.players[vm.human]
    Column(
        Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(Ink.PaperDeep.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            "Opponent (${vm.difficulty.displayName})",
            style = MaterialTheme.typography.labelLarge, color = Ink.Enemy,
        )
        Text(
            "⚔ ${s.unitsOf(1 - vm.human).size}/${GameEngine.MAX_UNITS_ON_FIELD}   ✋ ${opp.hand.size}   🂠 ${opp.deck.size}",
            style = MaterialTheme.typography.labelMedium, color = Ink.Enemy,
        )
        Text(
            "You   ⚔ ${s.unitsOf(vm.human).size}/${GameEngine.MAX_UNITS_ON_FIELD}   🂠 ${me.deck.size}   " + when (s.phase) {
                Phase.DEPLOY -> "· Deployment"
                Phase.BATTLE -> "· Turn ${s.turnNumber}"
                Phase.GAME_OVER -> "· Battle over"
            },
            style = MaterialTheme.typography.labelMedium, color = Ink.You,
        )
        if (s.fields.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }
        Spacer(Modifier.height(6.dp))
        PromptRow(vm, actions)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (s.stack.isNotEmpty()) ChainPanel(vm)
            SelectionPanel(vm, actions, onInspect)
        }
        SketchButton("Battle log", onLog, Modifier.fillMaxWidth(), small = true, color = Ink.PaperDeep)
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
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        when {
            d.kind == DecisionKind.DEPLOY && Action.EndDeploy in actions ->
                SketchButton("Done deploying", { vm.perform(Action.EndDeploy) }, Modifier.padding(top = 4.dp), small = true, color = Ink.Deploy)
            d.kind == DecisionKind.RESPOND && vm.humanToAct ->
                SketchButton("Pass", { vm.perform(Action.Pass) }, Modifier.padding(top = 4.dp), small = true, color = Ink.Gold)
            d.kind == DecisionKind.MAIN && vm.humanToAct ->
                SketchButton("Skip turn", { vm.perform(Action.Pass) }, Modifier.padding(top = 4.dp), small = true, color = Ink.PaperDeep)
        }
    }
}

@Composable
private fun ChainPanel(vm: GameViewModel) {
    val s = vm.state
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectionPanel(vm: GameViewModel, actions: List<Action>, onInspect: (UnitState) -> Unit) {
    val s = vm.state
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        when (val sel = vm.selection) {
            is Selection.Unit -> s.unit(sel.unitId)?.let { UnitInfo(vm, it, actions, onInspect) }
            is Selection.Card -> {
                val card = (s.players[vm.human].hand + s.players[vm.human].deck).firstOrNull { it.uid == sel.cardUid }
                if (card != null) {
                    val confirm = vm.confirmableActions(actions)
                    Column {
                        Text(card.def.name, style = MaterialTheme.typography.titleMedium)
                        Text(card.def.text, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (confirm.isEmpty()) "Choose a highlighted square on the board." else "Tap Play to use it.",
                            style = MaterialTheme.typography.labelMedium, color = Ink.Target,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            confirm.firstOrNull()?.let { SketchButton("Play", { vm.perform(it) }, small = true, color = Ink.Target) }
                            SketchButton("Cancel", { vm.selection = Selection.None }, small = true, color = Ink.PaperDeep)
                        }
                    }
                }
            }
            is Selection.Ability -> s.unit(sel.unitId)?.let { u ->
                val ab = u.abilities[sel.index].def
                Column {
                    Text("${u.name}: ${ab.name}", style = MaterialTheme.typography.titleMedium)
                    Text("${ab.text} Choose a highlighted target.", style = MaterialTheme.typography.bodySmall)
                    SketchButton("Cancel", { vm.selection = Selection.None }, Modifier.padding(top = 4.dp), small = true, color = Ink.PaperDeep)
                }
            }
            Selection.None -> Text(
                s.log.takeLast(4).asReversed().joinToString("\n"),
                style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
            )
        }
    }
}

@Composable
private fun UnitInfo(vm: GameViewModel, u: UnitState, actions: List<Action>, onInspect: (UnitState) -> Unit) {
    val s = vm.state
    val mine = u.owner == vm.human
    Column {
        Text(
            (if (u.isKing) "♛ " else "") + u.name,
            style = MaterialTheme.typography.titleMedium, color = if (mine) Ink.You else Ink.Enemy,
            modifier = Modifier.clickable { onInspect(u) },
        )
        Text(
            "⚔${GameEngine.attackOf(s, u)}  ♥${u.hp}/${u.maxHp}  👣${GameEngine.moveOf(s, u)}  🎯${GameEngine.rangeOf(s, u)}",
            style = MaterialTheme.typography.labelMedium,
        )
        val traits = buildList {
            u.keywords.forEach { add(it.displayName) }
            u.timedKeywords.forEach { add("${it.keyword.displayName} (${it.turns})") }
            if (u.stun > 0) add("Stunned ${u.stun}")
            if (u.poisonTurns > 0) add("Poison ${u.poisonDamage}×${u.poisonTurns}")
            if (u.shield > 0) add("Shield ${u.shield}")
            u.equipment.forEach { add("⛨ $it") }
        }
        if (traits.isNotEmpty()) Text(traits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        u.abilities.forEachIndexed { i, a ->
            val usable = mine && vm.abilityActions(u.id, i, actions).isNotEmpty()
            val label = a.def.name + (if (a.def.quick) " ⚡" else "") + (if (a.cooldown > 0) " (${a.cooldown})" else "")
            SketchButton(label, { vm.selectAbility(u.id, i) }, Modifier.padding(top = 4.dp), small = true, color = Ink.Target, enabled = usable)
            Text(a.def.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The player's cards. Expanded, they fan out in front of the board so a card can be picked;
 * docked, they sit in a slim strip on the left edge so the board is clear. Swipe the fan down
 * (or tap Hide) to dock it; tap the strip or swipe it right to bring the cards back.
 */
@Composable
private fun HandOverlay(
    vm: GameViewModel,
    actions: List<Action>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInspect: (CardDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = vm.handCards()
    val deploying = vm.state.phase == Phase.DEPLOY
    fun pick(uid: Int, playable: Boolean, def: CardDef) {
        if (!playable) {
            onInspect(def)
            return
        }
        vm.selectCard(uid)
        // A card that needs a square moves the hand aside so the board can be seen.
        if (vm.selection == Selection.Card(uid) && vm.confirmableActions(actions).isEmpty()) onExpandedChange(false)
    }
    Box(modifier) {
        AnimatedVisibility(
            expanded && cards.isNotEmpty(), Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount -> if (dragAmount > 12f) onExpandedChange(false) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (deploying) "Deployment pool — pick a unit, then a square" else "Your hand (${cards.size})",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(Ink.Paper.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    SketchButton("▼ Hide", { onExpandedChange(false) }, small = true, color = Ink.PaperDeep)
                }
                FannedCards(vm, cards, actions, onPick = ::pick, onInspect = onInspect)
            }
        }
        AnimatedVisibility(
            !expanded && cards.isNotEmpty(), Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            DockedCards(vm, cards, actions, onExpand = { onExpandedChange(true) }, onPick = ::pick, onInspect = onInspect)
        }
    }
}

@Composable
private fun FannedCards(
    vm: GameViewModel,
    cards: List<Pair<CardInstance, Int>>,
    actions: List<Action>,
    onPick: (Int, Boolean, CardDef) -> Unit,
    onInspect: (CardDef) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val cardWidth = 112.dp
        val n = cards.size
        val step = if (n <= 1) 0.dp else minOf(cardWidth + 8.dp, (maxWidth - cardWidth) / (n - 1))
        val total = cardWidth + step * (n - 1)
        val start = (maxWidth - total) / 2
        Box(Modifier.fillMaxWidth().height(cardWidth / CARD_ASPECT + 34.dp)) {
            cards.forEachIndexed { i, (card, count) ->
                val playable = vm.cardActions(card.uid, actions).isNotEmpty()
                val selected = vm.selection == Selection.Card(card.uid)
                val t = if (n <= 1) 0f else i / (n - 1f) - 0.5f
                CardFace(
                    card.def,
                    modifier = Modifier
                        .offset(x = start + step * i, y = if (selected) 0.dp else 16.dp + (abs(t) * 32).dp)
                        .rotate(t * 10f)
                        .zIndex(if (selected) 100f else i.toFloat()),
                    width = cardWidth,
                    selected = selected,
                    dimmed = vm.humanToAct && !playable,
                    badge = if (count > 1) "×$count" else null,
                    onClick = { onPick(card.uid, playable, card.def) },
                    onLongClick = { onInspect(card.def) },
                )
            }
        }
    }
}

@Composable
private fun DockedCards(
    vm: GameViewModel,
    cards: List<Pair<CardInstance, Int>>,
    actions: List<Action>,
    onExpand: () -> Unit,
    onPick: (Int, Boolean, CardDef) -> Unit,
    onInspect: (CardDef) -> Unit,
) {
    BoxWithConstraints {
        val n = cards.size
        // Overlap the mini cards more when the pool is large so the strip always fits.
        val step = if (n <= 1) 0.dp else minOf(24.dp, (maxHeight - 48.dp - 44.dp - 96.dp) / (n - 1)).coerceAtLeast(6.dp)
        Column(
            Modifier
                .padding(start = 4.dp, top = 48.dp, bottom = 8.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount -> if (dragAmount > 12f) onExpand() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SketchButton("▲ ${cards.sumOf { it.second }}", onExpand, small = true, color = Ink.Gold)
            Spacer(Modifier.height(4.dp))
            // Mini cards stacked like a tucked-away hand; the selected one sticks out.
            Box(Modifier.width(78.dp).height(step * (n - 1).coerceAtLeast(0) + 88.dp)) {
                cards.forEachIndexed { i, (card, count) ->
                    val playable = vm.cardActions(card.uid, actions).isNotEmpty()
                    val selected = vm.selection == Selection.Card(card.uid)
                    CardFace(
                        card.def,
                        modifier = Modifier
                            .offset(x = if (selected) 14.dp else 0.dp, y = step * i)
                            .zIndex(if (selected) 100f else i.toFloat()),
                        width = 62.dp,
                        selected = selected,
                        dimmed = vm.humanToAct && !playable,
                        badge = if (count > 1) "×$count" else null,
                        onClick = { onPick(card.uid, playable, card.def) },
                        onLongClick = { onInspect(card.def) },
                    )
                }
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
