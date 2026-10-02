package com.kingofthebeasts.core.tutorial

import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.Pos
import com.kingofthebeasts.core.game.Target
import com.kingofthebeasts.core.game.UnitState
import com.kingofthebeasts.core.model.Race

/**
 * One lesson of the tutorial. While it is the current step only the actions it [allows] can be
 * taken (answering "no" to an interrupt is always fine unless the step is about answering). It is
 * finished once [done] holds; an [info] step instead waits for the player to tap "Got it".
 */
class TutorialStep(
    val title: String,
    val text: String,
    val info: Boolean = false,
    /** True for the step that teaches answering an interrupt: passing is not allowed then. */
    val respond: Boolean = false,
    val allows: (GameState, Action) -> Boolean = { _, _ -> true },
    val done: (GameState) -> Boolean = { false },
)

/**
 * The tutorial battle: the Pride King's army against a rival Moon Howler whose moves are scripted.
 * Decks are kept in order and you go first, so every lesson lands the same way each time.
 */
object Tutorial {
    const val YOU = 0
    const val RIVAL = 1

    private const val KING = "l_king_pride"
    private const val LIONESS = "l_lioness"
    private const val RUNNER = "l_runner"
    private const val COURAGE = "l_courage"
    private const val BATTLE_CRY = "l_battlecry"
    private const val BANNER = "l_banner"

    private const val RIVAL_KING = "w_king_moon"
    private const val PUP = "w_pup"
    private const val HUNTER = "w_hunter"

    // Squares (columns A–H are x 0–7, rows 1–8 are y 0–7).
    val KING_SQUARE = Pos(3, 0) // D1
    val LIONESS_SQUARE = Pos(3, 2) // D3
    val RUNNER_SQUARE = Pos(4, 2) // E3
    val LIONESS_TARGET = Pos(3, 3) // D4, two squares from the pup
    private val RIVAL_KING_SQUARE = Pos(4, 7) // E8
    private val PUP_SQUARE = Pos(3, 5) // D6
    private val HUNTER_SQUARE = Pos(6, 6) // G7

    /** Your cards, in draw order: the three units to deploy, then the opening hand, then draws. */
    val playerDeck = Deck(
        "Tutorial: Pride", listOf(Race.LION),
        linkedMapOf(
            KING to 1, LIONESS to 1, RUNNER to 1,
            COURAGE to 1, BATTLE_CRY to 1, BANNER to 1, "l_sunfire" to 1, "l_valor" to 1,
            "l_sunbeam" to 1, "l_laurel" to 1, "l_spear" to 1, "l_mane" to 1, "l_gaze" to 1, "l_claws" to 1,
        ),
    )

    /** The rival has no Magic, so it never interrupts you. */
    val rivalDeck = Deck(
        "Tutorial: Moon Pack", listOf(Race.WOLF),
        linkedMapOf(
            RIVAL_KING to 1, PUP to 1, HUNTER to 1,
            "w_collar" to 3, "w_pelt" to 3, "w_necklace" to 3, "w_tracker" to 3,
        ),
    )

    fun newGame(): GameState =
        GameEngine.newGame(playerDeck, rivalDeck, listOf("You", "Rival"), seed = 2026L, shuffle = false, firstPlayer = YOU)

    private fun GameState.mine(cardId: String): UnitState? = unitsOf(YOU).firstOrNull { it.card.cardId == cardId }
    private fun GameState.theirs(cardId: String): UnitState? = unitsOf(RIVAL).firstOrNull { it.card.cardId == cardId }
    private fun GameState.cardOf(uid: Int): String? =
        (players[YOU].hand + players[YOU].deck).firstOrNull { it.uid == uid }?.cardId
    private fun GameState.used(cardId: String): Boolean =
        (players[YOU].discard + players[YOU].exhausted).any { it.cardId == cardId }

    private fun deploys(cardId: String, at: Pos): (GameState, Action) -> Boolean = { s, a ->
        a is Action.Deploy && a.pos == at && s.cardOf(a.cardUid) == cardId
    }

    private fun plays(cardId: String, onto: String? = null): (GameState, Action) -> Boolean = { s, a ->
        a is Action.PlayCard && s.cardOf(a.cardUid) == cardId &&
            (onto == null || (a.target as? Target.Unit)?.let { t -> s.unit(t.unitId)?.card?.cardId } == onto)
    }

    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            "Welcome to the battlefield",
            "This short battle teaches the basics. In every battle your goal is the same: defeat the enemy King. " +
                "Yours is the Pride King; your rival's is the Moon Howler.",
            info = true,
        ),
        TutorialStep(
            "Place your King",
            "Battles start with deployment. Pick the Pride King from your hand and tap the glowing square D1. " +
                "Your first three rows are your deployment zone, and the King always goes first.",
            allows = deploys(KING, KING_SQUARE),
            done = { it.mine(KING) != null },
        ),
        TutorialStep(
            "Place your army",
            "You and your rival take turns placing units. Put the Lioness Hunter on D3.",
            allows = deploys(LIONESS, LIONESS_SQUARE),
            done = { it.mine(LIONESS) != null },
        ),
        TutorialStep(
            "One more",
            "Now the Pride Runner on E3. It's fast: it can move 4 squares.",
            allows = deploys(RUNNER, RUNNER_SQUARE),
            done = { it.mine(RUNNER) != null },
        ),
        TutorialStep(
            "Look closer",
            "The battle begins and you've drawn your first cards. Press and hold any card in your hand, or any unit " +
                "on the board, to see it large with every rule explained. Try it on the Wolf Pup at D6 whenever you like.",
            info = true,
            done = { it.phase == Phase.BATTLE },
        ),
        TutorialStep(
            "Move",
            "Each turn you take one action: move, attack, use an ability or play a card. Tap your Lioness Hunter on D3, " +
                "then the glowing square D4.",
            allows = { s, a -> a is Action.Move && a.to == LIONESS_TARGET && s.unit(a.unitId)?.card?.cardId == LIONESS },
            done = { it.mine(LIONESS)?.pos == LIONESS_TARGET },
        ),
        TutorialStep(
            "Strategy",
            "The rival's Wolf Pup crept up next to your Lioness. Get ready: Strategy cards change the whole battlefield " +
                "until another Strategy replaces them. Play War Banner: your units get +1 attack and +1 movement.",
            allows = plays(BANNER),
            done = { s -> s.fields.any { it.cardId == BANNER } && s.stack.isEmpty() },
        ),
        TutorialStep(
            "Answer the attack!",
            "The Wolf Pup attacks your Lioness! When your rival acts, you may answer before it lands. Play Pride's Courage on your Lioness: its shield soaks up 2 of the damage.",
            respond = true,
            allows = plays(COURAGE, onto = LIONESS),
            done = { it.used(COURAGE) && it.stack.isEmpty() },
        ),
        TutorialStep(
            "The action queue",
            "Answers go on top of the action queue and resolve first: your shield went up, then the bite landed for " +
                "only 2. The rail on the right always shows the queue — tap Details (or swipe it left) for the full story.",
            info = true,
        ),
        TutorialStep(
            "Quick spells",
            "Magic cards boost your units. Quick spells (marked Quick) don't even use up your action. " +
                "Play Battle Cry on your Lioness: +1 attack this turn — and it's still your move.",
            allows = plays(BATTLE_CRY, onto = LIONESS),
            done = { it.used(BATTLE_CRY) && it.stack.isEmpty() },
        ),
        TutorialStep(
            "Attack",
            "Now strike! Tap your Lioness, then the Wolf Pup. With 6 attack (War Banner, Battle Cry, and +1 from your " +
                "King's Commander aura) she defeats it.",
            allows = { s, a ->
                a is Action.Attack && s.unit(a.unitId)?.card?.cardId == LIONESS && s.unit(a.targetId)?.card?.cardId == PUP
            },
            done = { s -> s.theirs(PUP) == null && s.stack.isEmpty() },
        ),
        TutorialStep(
            "Defeat the King",
            "Finish it: march on the Moon Howler at E8 and attack it. Kings and Champions may move and then attack in one " +
                "turn; other units move or attack. Kings can't be hurt by spells, only by attacks. Your Pride King fights too, and allies " +
                "within 3 squares of him get +1 attack. Everything is allowed now.",
            done = { it.phase == Phase.GAME_OVER },
        ),
    )

    /** Whether the player may take [action] during [step]. */
    fun allowed(step: TutorialStep, s: GameState, action: Action): Boolean {
        if (step.info) return false
        val responding = GameEngine.decision(s).kind == DecisionKind.RESPOND
        if (responding && !step.respond && action == Action.Pass) return true
        return step.allows(s, action)
    }

    /** The first step not yet finished, starting from [from]; info steps wait to be acknowledged. */
    fun advance(s: GameState, from: Int): Int {
        var i = from
        while (i < steps.size - 1 && !steps[i].info && steps[i].done(s)) i++
        return i
    }

    /**
     * The rival's move. It deploys its three units, then waits — except that its Wolf Pup creeps up to
     * your Lioness and, a turn later, bites her (once), so you can learn to answer an interrupt.
     */
    fun rivalAction(s: GameState): Action {
        val legal = GameEngine.legalActions(s)
        val d = GameEngine.decision(s)
        fun deploy(cardId: String, at: Pos) = legal.firstOrNull {
            it is Action.Deploy && it.pos == at && s.players[RIVAL].deck.firstOrNull { c -> c.uid == it.cardUid }?.cardId == cardId
        }
        return when (d.kind) {
            DecisionKind.DEPLOY -> when (s.players[RIVAL].deployed) {
                0 -> deploy(RIVAL_KING, RIVAL_KING_SQUARE)
                1 -> deploy(PUP, PUP_SQUARE)
                2 -> deploy(HUNTER, HUNTER_SQUARE)
                else -> null
            } ?: legal.firstOrNull { it == Action.EndDeploy } ?: legal.first()
            DecisionKind.MAIN -> {
                val pup = s.theirs(PUP)
                val lioness = s.mine(LIONESS)
                if (pup == null || lioness == null || s.used(COURAGE)) return Action.Pass
                val bite = Action.Attack(pup.id, lioness.id)
                if (bite in legal) return bite
                // Step up next to her (she has moved to D4); the bite comes next turn.
                legal.filterIsInstance<Action.Move>()
                    .filter { it.unitId == pup.id && it.to.distanceTo(lioness.pos) == 1 }
                    .minByOrNull { it.to.distanceTo(pup.pos) * 10 + it.to.x }
                    ?: Action.Pass
            }
            else -> Action.Pass
        }
    }
}
