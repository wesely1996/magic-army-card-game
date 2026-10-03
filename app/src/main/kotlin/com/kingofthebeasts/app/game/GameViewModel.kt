package com.kingofthebeasts.app.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kingofthebeasts.app.net.RemoteSide
import com.kingofthebeasts.app.settings.AppSettings
import com.kingofthebeasts.core.net.NetMessage
import com.kingofthebeasts.core.profile.MatchRecord
import com.kingofthebeasts.core.net.checksum
import com.kingofthebeasts.core.tutorial.Tutorial
import com.kingofthebeasts.core.tutorial.TutorialStep
import androidx.lifecycle.viewModelScope
import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.ActionCodec
import com.kingofthebeasts.core.game.CardInstance
import com.kingofthebeasts.core.game.Decision
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.Pos
import com.kingofthebeasts.core.game.SavedBattle
import com.kingofthebeasts.core.game.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Selection {
    data object None : Selection
    data class Unit(val unitId: Int) : Selection
    data class Card(val cardUid: Int) : Selection
    data class Ability(val unitId: Int, val index: Int) : Selection
}

data class Highlights(
    val move: Set<Pos> = emptySet(),
    val attack: Set<Pos> = emptySet(),
    val target: Set<Pos> = emptySet(),
    val selected: Pos? = null,
)

/**
 * Owns one battle. Against the AI the human is player 0 (near side of the board) and the AI
 * player 1; the AI thinks off the main thread. Online, [remote] is the friend's app and the human
 * may be either player. The game state is only ever mutated on the main thread.
 */
class GameViewModel(
    /** Engine player 0's deck (the human's, against the AI; the host's, online). */
    val deck0: Deck,
    val deck1: Deck,
    val difficulty: Difficulty,
    private val seed: Long,
    /** Actions of a saved battle to replay (resume), encoded with [ActionCodec]. */
    resume: List<String> = emptyList(),
    /** Called after every action with the battle to save, or null once it is over. */
    private val onSave: (SavedBattle?) -> Unit = {},
    /** Online: the friend's side of the game. Null against the AI. */
    private val remote: RemoteSide? = null,
    /** Which engine player is on this phone. */
    val human: Int = 0,
    names: List<String> = listOf("You", "Opponent"),
    /** The guided tutorial battle: a fixed setup, a scripted rival and lessons that limit your moves. */
    val tutorial: Boolean = false,
    /** Called once when the battle is decided (or given up), with the record for the match history. */
    private val onFinished: (MatchRecord) -> Unit = {},
) : ViewModel() {
    val state: GameState = if (tutorial) Tutorial.newGame() else GameEngine.newGame(deck0, deck1, names, seed)

    /** The current tutorial lesson (index into [Tutorial.steps]). */
    var tutorialStep by mutableIntStateOf(0)
        private set
    val lesson: TutorialStep? get() = if (tutorial) Tutorial.steps[tutorialStep] else null

    /** "Got it" on an explanation-only lesson. */
    fun acknowledgeLesson() {
        val step = lesson ?: return
        if (!step.info) return
        tutorialStep = Tutorial.advance(state, (tutorialStep + 1).coerceAtMost(Tutorial.steps.size - 1))
    }

    private fun updateLesson() {
        if (tutorial) tutorialStep = Tutorial.advance(state, tutorialStep)
    }
    val online: Boolean get() = remote != null
    /** How the opponent is called on screen. */
    val opponentLabel: String
        get() = remote?.opponentName ?: if (tutorial) "Rival" else "Opponent (${difficulty.displayName})"

    /** The two copies of an online game disagree (or the friend sent something impossible). */
    var desynced by mutableStateOf(false)
        private set

    /** Online: seconds left for the current decision, or null when there's no clock (AI battles, tutorial). */
    var secondsLeft by mutableStateOf<Int?>(null)
        private set
    private var clock: Job? = null
    private val timeoutPlayer by lazy { AiPlayer(Difficulty.EASY, seed + 99) }

    /** Online games give each decision [TURN_SECONDS]; when yours runs out your app passes (or deploys) for you. */
    private fun restartClock() {
        if (remote == null) return
        clock?.cancel()
        val d = GameEngine.decision(state)
        if (d.kind == DecisionKind.NONE || desynced) {
            secondsLeft = null
            return
        }
        val mine = d.player == human
        val decisionAt = history.size
        clock = viewModelScope.launch {
            for (t in TURN_SECONDS downTo 1) {
                secondsLeft = t
                delay(1000)
            }
            secondsLeft = 0
            if (mine && history.size == decisionAt && humanToAct) {
                val legal = GameEngine.legalActions(state)
                val action = when {
                    Action.Pass in legal -> Action.Pass
                    else -> timeoutPlayer.choose(state).takeIf { it in legal } ?: legal.first()
                }
                perform(action)
            }
        }
    }
    private val ai = AiPlayer(difficulty, seed * 31 + 7 + resume.size)
    private var aiJob: Job? = null

    /** Every action taken so far (encoded), for saving and resuming. */
    private val history = mutableListOf<String>()

    /** Events up to this sequence number happened before a resume and shouldn't be animated again. */
    val resumedEventSeq: Int

    /** Bumped after every state change so Compose re-reads the (mutable) game state. */
    var version by mutableIntStateOf(0)
        private set
    var selection by mutableStateOf<Selection>(Selection.None)
    var aiThinking by mutableStateOf(false)
        private set

    init {
        for (code in resume) {
            val a = ActionCodec.decode(code)
            GameEngine.apply(state, a)
            history += code
        }
        resumedEventSeq = if (resume.isEmpty()) 0 else state.eventSeq
        if (remote != null) {
            viewModelScope.launch { for (act in remote.incoming) receive(act) }
            restartClock()
        } else {
            runAi()
        }
    }

    /** The battle as it stands, for [onSave]. */
    fun snapshot(): SavedBattle = SavedBattle(deck0, deck1, difficulty.name, seed, history.toList(), state.turnNumber)

    private var resultRecorded = false

    /**
     * Hands the result to [onFinished], once: when the battle is over, or when a player gives up
     * ([forfeitBy] is the engine player who did) before it is. The tutorial isn't recorded.
     */
    fun recordResult(forfeitBy: Int? = null) {
        if (resultRecorded || tutorial) return
        val over = state.phase == Phase.GAME_OVER
        if (forfeitBy == null && !over || forfeitBy != null && over) return
        resultRecorded = true
        val mine = if (human == 0) deck0 else deck1
        val theirs = if (human == 0) deck1 else deck0
        onFinished(
            MatchRecord.of(
                state, human, mine, theirs, difficulty.takeIf { remote == null }, remote?.opponentName,
                System.currentTimeMillis(), forfeitBy,
            ),
        )
    }

    private fun record(action: Action) {
        history += ActionCodec.encode(action)
        updateLesson()
        recordResult()
        if (state.phase == Phase.GAME_OVER) remote?.finished()
        // Online games (and the tutorial) can't be resumed: the friend's app would have moved on.
        if (remote == null && !tutorial) onSave(if (state.phase == Phase.GAME_OVER) null else snapshot())
    }

    val decision: Decision get() = GameEngine.decision(state)
    val humanToAct: Boolean
        get() = decision.player == human && decision.kind != DecisionKind.NONE && !aiThinking && !desynced

    fun legalActions(): List<Action> = when {
        !humanToAct -> emptyList()
        tutorial -> GameEngine.legalActions(state).filter { Tutorial.allowed(lesson!!, state, it) }
        else -> GameEngine.legalActions(state)
    }

    fun perform(action: Action) {
        if (!humanToAct || action !in legalActions()) return
        GameEngine.apply(state, action)
        record(action)
        remote?.send(history.size - 1, history.last(), state.checksum())
        selection = Selection.None
        version++
        restartClock()
        runAi()
    }

    /** An action from the friend's app: it must be their move, legal, and leave both copies equal. */
    private fun receive(act: NetMessage.Act) {
        if (desynced) return
        val action = runCatching { ActionCodec.decode(act.code) }.getOrNull()
        val d = decision
        if (action == null || act.index != history.size || d.player != 1 - human || d.kind == DecisionKind.NONE ||
            !GameEngine.isLegal(state, action)
        ) {
            desynced = true
            return
        }
        GameEngine.apply(state, action)
        record(action)
        if (state.checksum() != act.checksum) desynced = true
        version++
        restartClock()
    }

    private fun runAi() {
        if (remote != null || aiJob?.isActive == true) return
        aiJob = viewModelScope.launch {
            while (true) {
                val d = GameEngine.decision(state)
                if (d.kind == DecisionKind.NONE || d.player == human) break
                aiThinking = true
                val started = System.currentTimeMillis()
                val action = if (tutorial) Tutorial.rivalAction(state) else withContext(Dispatchers.Default) { ai.choose(state) }
                // Keep a readable pace even when the AI decides instantly.
                val minPause = if (d.kind == DecisionKind.DEPLOY) 600L else 1000L
                val pause = (minPause / AppSettings.animationSpeed.factor).toLong()
                delay((pause - (System.currentTimeMillis() - started)).coerceAtLeast(0L))
                GameEngine.apply(state, action)
                record(action)
                version++
            }
            aiThinking = false
        }
    }

    // ------------------------------------------------------------ selection

    /** Cards shown in the hand row: the deploy pool during deployment, the hand in battle. */
    fun handCards(): List<Pair<CardInstance, Int>> =
        if (state.phase == Phase.DEPLOY) {
            GameEngine.deployableCards(state, human).groupBy { it.cardId }.map { (_, l) -> l.first() to l.size }
        } else {
            state.players[human].hand.map { it to 1 }
        }

    fun cardActions(uid: Int, actions: List<Action>) = actions.filter {
        (it is Action.PlayCard && it.cardUid == uid) || (it is Action.Deploy && it.cardUid == uid)
    }

    fun abilityActions(unitId: Int, index: Int, actions: List<Action>) =
        actions.filterIsInstance<Action.UseAbility>().filter { it.unitId == unitId && it.abilityIndex == index }

    fun selectCard(uid: Int) {
        selection = if (selection == Selection.Card(uid)) Selection.None else Selection.Card(uid)
    }

    fun selectAbility(unitId: Int, index: Int) {
        val options = abilityActions(unitId, index, legalActions())
        val direct = options.singleOrNull()?.takeIf { it.target.isSelfOrNone(unitId) }
        if (direct != null) perform(direct) else selection = Selection.Ability(unitId, index)
    }

    private fun Target.isSelfOrNone(unitId: Int) = this !is Target.Unit || this.unitId == unitId

    /** Actions that need no board target (strategies, counters, self abilities). */
    fun confirmableActions(actions: List<Action>): List<Action> = when (val sel = selection) {
        is Selection.Card -> cardActions(sel.cardUid, actions).filterIsInstance<Action.PlayCard>()
            .filter { it.target is Target.None || it.target is Target.StackEntry }
        is Selection.Ability -> abilityActions(sel.unitId, sel.index, actions).filter { it.target.isSelfOrNone(sel.unitId) }
        else -> emptyList()
    }

    fun onTileTapped(pos: Pos) {
        val actions = legalActions()
        actionForTile(pos, actions)?.let {
            perform(it)
            return
        }
        val u = state.unitAt(pos)
        selection = if (u != null && selection != Selection.Unit(u.id)) Selection.Unit(u.id) else Selection.None
    }

    private fun Target.at(pos: Pos): Boolean = when (this) {
        is Target.Tile -> this.pos == pos
        is Target.Unit -> state.unit(unitId)?.pos == pos
        else -> false
    }

    private fun actionForTile(pos: Pos, actions: List<Action>): Action? = when (val sel = selection) {
        is Selection.Unit -> actions.firstOrNull {
            (it is Action.Move && it.unitId == sel.unitId && it.to == pos) ||
                (it is Action.Attack && it.unitId == sel.unitId && state.unit(it.targetId)?.pos == pos)
        }
        is Selection.Card -> cardActions(sel.cardUid, actions).firstOrNull {
            (it is Action.Deploy && it.pos == pos) || (it is Action.PlayCard && it.target.at(pos))
        }
        is Selection.Ability -> abilityActions(sel.unitId, sel.index, actions).firstOrNull { it.target.at(pos) }
        Selection.None -> null
    }

    fun highlights(actions: List<Action>): Highlights = when (val sel = selection) {
        is Selection.Unit -> Highlights(
            move = actions.filterIsInstance<Action.Move>().filter { it.unitId == sel.unitId }.map { it.to }.toSet(),
            attack = actions.filterIsInstance<Action.Attack>().filter { it.unitId == sel.unitId }
                .mapNotNull { state.unit(it.targetId)?.pos }.toSet(),
            selected = state.unit(sel.unitId)?.pos,
        )
        is Selection.Card -> Highlights(
            target = cardActions(sel.cardUid, actions).mapNotNull {
                when (it) {
                    is Action.Deploy -> it.pos
                    is Action.PlayCard -> when (val t = it.target) {
                        is Target.Tile -> t.pos
                        is Target.Unit -> state.unit(t.unitId)?.pos
                        else -> null
                    }
                    else -> null
                }
            }.toSet(),
        )
        is Selection.Ability -> Highlights(
            target = abilityActions(sel.unitId, sel.index, actions)
                .mapNotNull { (it.target as? Target.Unit)?.let { t -> state.unit(t.unitId)?.pos } }.toSet(),
            selected = state.unit(sel.unitId)?.pos,
        )
        Selection.None -> Highlights()
    }

    companion object {
        /** Seconds per decision in online battles. */
        const val TURN_SECONDS = 20
    }
}
