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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.kingofthebeasts.app.ui.CARD_ASPECT
import com.kingofthebeasts.app.ui.CardFace
import com.kingofthebeasts.app.ui.CardInspectDialog
import com.kingofthebeasts.app.ui.SketchButton
import com.kingofthebeasts.app.ui.brushUnderline
import com.kingofthebeasts.app.ui.sketchBorder
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.app.ui.watercolor
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.CardInstance
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameEvent
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.StackItem
import com.kingofthebeasts.core.game.Target
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

    // The right-hand drawer: a slim rail with the action queue, pulled out for the full story.
    var drawerOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Box(Modifier.fillMaxSize().background(Ink.Paper)) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.fillMaxSize().padding(end = RAIL_WIDTH)) {
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
            ActionRail(vm, actions, onOpen = { drawerOpen = true }, modifier = Modifier.align(Alignment.CenterEnd))
            AnimatedVisibility(drawerOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink.Line.copy(alpha = 0.25f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { drawerOpen = false },
                )
            }
            AnimatedVisibility(
                drawerOpen, Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it }, exit = slideOutHorizontally { it },
            ) {
                StatusDrawer(
                    vm, actions,
                    onClose = { drawerOpen = false },
                    onLog = { showLog = true },
                    onInspect = { u -> detail = u.def to u.id },
                )
            }
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

private val RAIL_WIDTH = 124.dp

/**
 * The collapsed drawer: whose move it is, the action queue (the interrupt chain) in short form,
 * and the buttons needed right now. Tap "Details" or swipe left for the full explanation.
 */
@Composable
private fun ActionRail(vm: GameViewModel, actions: List<Action>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val s = vm.state
    val (status, statusColor) = statusLine(vm)
    Column(
        modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(Ink.PaperDeep.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> if (dx < -12f) onOpen() } }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SketchButton("◀ Details", onOpen, Modifier.fillMaxWidth(), small = true, color = Ink.PaperDeep)
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.titleSmall, color = statusColor, textAlign = TextAlign.Center)
        Text(
            when (s.phase) {
                Phase.DEPLOY -> "Deployment"
                Phase.BATTLE -> "Turn ${s.turnNumber}"
                Phase.GAME_OVER -> "Battle over"
            },
            style = MaterialTheme.typography.labelSmall, color = Ink.Faded,
        )
        Row {
            Text("⚔${s.unitsOf(vm.human).size}", style = MaterialTheme.typography.labelMedium, color = Ink.You)
            Text("  vs  ", style = MaterialTheme.typography.labelMedium, color = Ink.Faded)
            Text("⚔${s.unitsOf(1 - vm.human).size}", style = MaterialTheme.typography.labelMedium, color = Ink.Enemy)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (s.stack.isEmpty()) "Chain empty" else "⚡ Chain · ${s.stack.size}",
            style = MaterialTheme.typography.labelMedium, color = if (s.stack.isEmpty()) Ink.Faded else Ink.Line,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Top of the chain first: it resolves first.
            s.stack.asReversed().forEachIndexed { i, item ->
                Text(
                    "${i + 1}. ${shortLabel(s, item)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = if (item.countered) TextDecoration.LineThrough else null,
                    ),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .watercolor(if (item.controller == vm.human) Ink.You else Ink.Enemy, item.id, 0.8f)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            if (s.stack.isEmpty()) {
                // The latest thing that happened (skipping the "— Turn N —" separators).
                Text(s.log.lastOrNull { !it.startsWith("—") }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Ink.Faded, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        RailSelection(vm, actions)
        PrimaryButton(vm, actions, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/** Compact controls for whatever is selected: Play/Cancel for a card, ability buttons for a unit. */
@Composable
private fun RailSelection(vm: GameViewModel, actions: List<Action>) {
    val s = vm.state
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val sel = vm.selection) {
            is Selection.Card -> {
                val card = (s.players[vm.human].hand + s.players[vm.human].deck).firstOrNull { it.uid == sel.cardUid } ?: return@Column
                val confirm = vm.confirmableActions(actions)
                Text(card.def.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (confirm.isEmpty()) Text("Pick a square", style = MaterialTheme.typography.labelSmall, color = Ink.Target)
                confirm.firstOrNull()?.let { SketchButton("Play", { vm.perform(it) }, Modifier.fillMaxWidth(), small = true, color = Ink.Target) }
                SketchButton("Cancel", { vm.selection = Selection.None }, Modifier.fillMaxWidth(), small = true, color = Ink.PaperDeep)
            }
            is Selection.Ability -> {
                val u = s.unit(sel.unitId) ?: return@Column
                Text(u.abilities.getOrNull(sel.index)?.def?.name.orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                Text("Pick a target", style = MaterialTheme.typography.labelSmall, color = Ink.Target)
                SketchButton("Cancel", { vm.selection = Selection.None }, Modifier.fillMaxWidth(), small = true, color = Ink.PaperDeep)
            }
            is Selection.Unit -> {
                val u = s.unit(sel.unitId) ?: return@Column
                val mine = u.owner == vm.human
                Text(
                    (if (u.isKing) "♛ " else "") + u.name,
                    style = MaterialTheme.typography.labelMedium, color = if (mine) Ink.You else Ink.Enemy,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text("♥${u.hp}/${u.maxHp}  ⚔${GameEngine.attackOf(s, u)}", style = MaterialTheme.typography.labelSmall)
                if (mine) u.abilities.forEachIndexed { i, a ->
                    val usable = vm.abilityActions(u.id, i, actions).isNotEmpty()
                    if (usable) SketchButton(
                        a.def.name + if (a.def.quick) " ⚡" else "", { vm.selectAbility(u.id, i) },
                        Modifier.fillMaxWidth(), small = true, color = Ink.Target,
                    )
                }
            }
            Selection.None -> Unit
        }
    }
}

/** Done deploying / Pass / Skip turn, whichever the current decision offers. */
@Composable
private fun PrimaryButton(vm: GameViewModel, actions: List<Action>, modifier: Modifier = Modifier) {
    val d = vm.decision
    when {
        d.kind == DecisionKind.DEPLOY && Action.EndDeploy in actions ->
            SketchButton("Done", { vm.perform(Action.EndDeploy) }, modifier, small = true, color = Ink.Deploy)
        d.kind == DecisionKind.RESPOND && vm.humanToAct ->
            SketchButton("Pass", { vm.perform(Action.Pass) }, modifier, small = true, color = Ink.Gold)
        d.kind == DecisionKind.MAIN && vm.humanToAct ->
            SketchButton("Skip turn", { vm.perform(Action.Pass) }, modifier, small = true, color = Ink.PaperDeep)
    }
}

private fun statusLine(vm: GameViewModel): Pair<String, Color> {
    val s = vm.state
    val d = vm.decision
    return when {
        s.phase == Phase.GAME_OVER -> "Game over" to Ink.Line
        vm.aiThinking || d.player != vm.human -> "Opponent…" to Ink.Enemy
        d.kind == DecisionKind.RESPOND -> "Respond?" to Ink.Target
        d.kind == DecisionKind.DEPLOY -> "Deploy" to Ink.Deploy
        else -> "Your move" to Ink.You
    }
}

/**
 * A few words for a chain entry, e.g. "Sky Strike → Pride King" or "Dire Wolf ⚔ Cobra".
 * [verbose] spells out answers to other entries ("against Wolf Scout" instead of "vs #2").
 */
private fun shortLabel(s: GameState, item: StackItem, verbose: Boolean = false): String {
    fun unitName(id: Int) = s.unit(id)?.name ?: "(gone)"
    fun target(t: Target) = when (t) {
        Target.None -> ""
        is Target.Unit -> " → ${unitName(t.unitId)}"
        is Target.Tile -> " → ${t.pos}"
        is Target.StackEntry -> {
            val i = s.stack.indexOfFirst { it.id == t.itemId }
            when {
                i < 0 -> ""
                verbose -> " against “${actionName(s, s.stack[i])}”"
                else -> " vs #${s.stack.size - i}"
            }
        }
    }
    return when (val a = item.action) {
        is Action.PlayCard -> actionName(s, item) + target(a.target)
        is Action.UseAbility -> actionName(s, item) + target(a.target)
        is Action.Attack -> "${unitName(a.unitId)} ⚔ ${unitName(a.targetId)}"
        is Action.Move -> "${unitName(a.unitId)} → ${a.to}"
        else -> item.label
    }
}

/** The card or ability name behind a chain entry. */
private fun actionName(s: GameState, item: StackItem): String = when (val a = item.action) {
    is Action.PlayCard -> item.card?.def?.name ?: item.label
    is Action.UseAbility -> s.unit(a.unitId)?.abilities?.getOrNull(a.abilityIndex)?.def?.name ?: item.label
    is Action.Attack -> "${s.unit(a.unitId)?.name ?: "(gone)"} attacks"
    is Action.Move -> "${s.unit(a.unitId)?.name ?: "(gone)"} moves"
    else -> item.label
}

/** What a chain entry will do when it resolves. */
private fun chainDetail(s: GameState, item: StackItem): String = when (val a = item.action) {
    is Action.PlayCard -> item.card?.def?.text.orEmpty()
    is Action.UseAbility -> s.unit(a.unitId)?.abilities?.getOrNull(a.abilityIndex)?.def?.text
        ?: "The unit is gone, so this will fizzle."
    is Action.Attack -> {
        val attacker = s.unit(a.unitId)
        val target = s.unit(a.targetId)
        if (attacker == null || target == null) "Attacker or target is gone, so this will fizzle."
        else "Deals ${GameEngine.attackDamage(s, attacker, target)} damage before armor and shields. ${target.name} has ${target.hp} health."
    }
    is Action.Move -> "Moves to ${a.to} if the path is still open."
    else -> ""
}

/** The situation in plain words, for the expanded drawer. */
private fun explanation(vm: GameViewModel): Pair<String, String> {
    val s = vm.state
    val d = vm.decision
    val me = s.players[vm.human]
    val top = s.stack.lastOrNull()
    return when {
        s.phase == Phase.GAME_OVER -> "The battle is over." to "A King has fallen."
        (vm.aiThinking || d.player != vm.human) && top != null ->
            "The opponent is deciding whether to answer." to
                "Next to resolve: ${s.players[top.controller].name}'s ${shortLabel(s, top, verbose = true)}. The opponent may add a Magic card or ⚡ quick ability of their own; " +
                "otherwise the chain resolves from the top down."
        vm.aiThinking || d.player != vm.human ->
            (if (s.phase == Phase.DEPLOY) "The opponent is placing a unit." else "The opponent is taking their turn.") to
                "Each turn a player draws a card and takes one action. When they act you'll get the chance to interrupt."
        d.kind == DecisionKind.DEPLOY && me.deployed == 0 ->
            "Place your King." to "Pick your King from the hand and tap a highlighted square in your first 3 rows. The King always goes first."
        d.kind == DecisionKind.DEPLOY ->
            "Deploy your army (${me.deployed}/${GameEngine.MAX_DEPLOY})." to
                "Players take turns placing one unit in their first 3 rows, up to ${GameEngine.MAX_DEPLOY} each. " +
                "Tap Done when you have placed enough; your remaining units are shuffled into your deck."
        d.kind == DecisionKind.RESPOND ->
            "Your chance to respond." to
                (top?.let { "Next to resolve: ${s.players[it.controller].name}'s ${shortLabel(s, it, verbose = true)}. " } ?: "") +
                "You may respond with a Magic card or a ⚡ quick ability — the newest answer resolves first. " +
                "If you pass, the whole chain resolves from the top down, and actions that no longer make sense fizzle."
        s.blitzUsed -> "Blitz! Take one more action." to "Blitz lets you move once without ending your turn."
        else -> "Your turn: take one action." to
            "Move a unit, attack, use an ability or play a card. Tap one of your units to see where it can move and what it can attack, " +
            "or pick a card from your hand. Your opponent may interrupt before your action resolves."
    }
}

/** The expanded drawer: what is going on, in detail. */
@Composable
private fun StatusDrawer(
    vm: GameViewModel,
    actions: List<Action>,
    onClose: () -> Unit,
    onLog: () -> Unit,
    onInspect: (UnitState) -> Unit,
) {
    val s = vm.state
    val (headline, detail) = explanation(vm)
    Column(
        Modifier
            .width(340.dp)
            .fillMaxHeight()
            .background(Ink.Paper)
            .sketchBorder(seed = 77)
            .pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> if (dx > 12f) onClose() } }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("What's going on", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            SketchButton("▶", onClose, small = true, color = Ink.PaperDeep)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(headline, style = MaterialTheme.typography.titleMedium, color = statusLine(vm).second)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            PrimaryButton(vm, actions)

            if (s.stack.isNotEmpty()) {
                DrawerSection("Action queue — resolves from the top")
                s.stack.asReversed().forEachIndexed { i, item ->
                    val color = if (item.controller == vm.human) Ink.You else Ink.Enemy
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .watercolor(color, item.id, 0.6f)
                            .sketchBorder(seed = item.id)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "${ordinal(i + 1)} · ${s.players[item.controller].name}",
                            style = MaterialTheme.typography.labelSmall, color = color,
                        )
                        Text(shortLabel(s, item, verbose = true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        chainDetail(s, item).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (item.countered) Text("Countered — it will fizzle.", style = MaterialTheme.typography.labelSmall, color = Ink.Enemy)
                    }
                }
            }

            if (vm.selection != Selection.None) {
                DrawerSection("Selected")
                SelectionPanel(vm, actions, onInspect)
            }

            DrawerSection("Armies")
            for (p in listOf(vm.human, 1 - vm.human)) {
                val player = s.players[p]
                val king = s.king(p)
                Text(
                    if (p == vm.human) "You" else "Opponent (${vm.difficulty.displayName})",
                    style = MaterialTheme.typography.labelLarge, color = if (p == vm.human) Ink.You else Ink.Enemy,
                )
                Text(
                    (king?.let { "♛ ${it.name} ${it.hp}/${it.maxHp} health · " } ?: "") +
                        "${s.unitsOf(p).size}/${GameEngine.MAX_UNITS_ON_FIELD} units · ${player.hand.size} in hand · ${player.deck.size} in deck",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (s.fields.isNotEmpty()) {
                DrawerSection("Battlefield rules")
                for (f in s.fields) {
                    Text(
                        "⚑ ${f.rule.displayName} (${if (f.owner == vm.human) "yours" else "opponent's"}, ${f.turns} turn(s) left)",
                        style = MaterialTheme.typography.labelMedium, color = if (f.owner == vm.human) Ink.You else Ink.Enemy,
                    )
                    Text(f.rule.description, style = MaterialTheme.typography.bodySmall)
                }
            }

            DrawerSection("Recent events")
            s.log.takeLast(8).asReversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink.Faded) }
        }
        SketchButton("Full battle log", onLog, Modifier.fillMaxWidth().padding(top = 6.dp), small = true, color = Ink.PaperDeep)
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp).brushUnderline(Ink.Gold, title.length))
}

private fun ordinal(n: Int) = when (n) {
    1 -> "Resolves 1st"
    2 -> "Resolves 2nd"
    3 -> "Resolves 3rd"
    else -> "Resolves ${n}th"
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
