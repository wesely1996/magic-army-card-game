package com.kingofthebeasts.core.game

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.model.AbilityDef
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.FieldRule
import com.kingofthebeasts.core.model.Keyword
import com.kingofthebeasts.core.model.RacialTrait
import kotlin.math.abs
import kotlin.math.max

data class Pos(val x: Int, val y: Int) {
    fun distanceTo(o: Pos): Int = max(abs(x - o.x), abs(y - o.y))
    val onBoard: Boolean get() = x in 0 until Board.SIZE && y in 0 until Board.SIZE
    val isBorder: Boolean get() = x == 0 || y == 0 || x == Board.SIZE - 1 || y == Board.SIZE - 1

    fun neighbors(): List<Pos> = buildList {
        for (dx in -1..1) for (dy in -1..1) {
            if (dx == 0 && dy == 0) continue
            val p = Pos(x + dx, y + dy)
            if (p.onBoard) add(p)
        }
    }

    /** Chess-style name, e.g. "c4". Player 0 sits at row 1. */
    /** Chess-style square name, e.g. "G4". */
    override fun toString(): String = "${'A' + x}${y + 1}"
}

object Board {
    const val SIZE = 8
    const val DEPLOY_ROWS = 3

    val allTiles: List<Pos> = (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> Pos(x, y) } }

    fun isDeployZone(player: Int, p: Pos): Boolean =
        if (player == 0) p.y < DEPLOY_ROWS else p.y >= SIZE - DEPLOY_ROWS

    fun isOwnHalf(player: Int, p: Pos): Boolean =
        if (player == 0) p.y < SIZE / 2 else p.y >= SIZE / 2
}

data class CardInstance(val uid: Int, val cardId: String) {
    val def: CardDef get() = CardDatabase.get(cardId)
}

class TimedMod(val attack: Int, val move: Int, val range: Int, var turns: Int)
class TimedKeyword(val keyword: Keyword, var turns: Int)
class AbilityState(val def: AbilityDef, var cooldown: Int = 0)

class UnitState(
    val id: Int,
    val card: CardInstance,
    var owner: Int,
    var pos: Pos,
    var maxHp: Int,
    var hp: Int,
    var attack: Int,
    var move: Int,
    var range: Int,
    val keywords: MutableSet<Keyword>,
    val timedKeywords: MutableList<TimedKeyword> = mutableListOf(),
    val mods: MutableList<TimedMod> = mutableListOf(),
    var stun: Int = 0,
    var poisonDamage: Int = 0,
    var poisonTurns: Int = 0,
    var shield: Int = 0,
    val abilities: MutableList<AbilityState> = mutableListOf(),
    val equipment: MutableList<String> = mutableListOf(),
    val isKing: Boolean = false,
    val isToken: Boolean = false,
    /** Brood: how many more Swarm Rats it will spawn at the start of its owner's turns. */
    var broodLeft: Int = 0,
) {
    val def: CardDef get() = card.def
    val name: String get() = def.name

    /** Name with the square it stands on, e.g. "Dire Wolf (G4)". */
    val tag: String get() = "$name ($pos)"
    val alive: Boolean get() = hp > 0

    fun has(k: Keyword): Boolean = k in keywords || timedKeywords.any { it.keyword == k }

    fun copy(): UnitState = UnitState(
        id, card, owner, pos, maxHp, hp, attack, move, range, keywords.toMutableSet(),
        timedKeywords.mapTo(mutableListOf()) { TimedKeyword(it.keyword, it.turns) },
        mods.mapTo(mutableListOf()) { TimedMod(it.attack, it.move, it.range, it.turns) },
        stun, poisonDamage, poisonTurns, shield,
        abilities.mapTo(mutableListOf()) { AbilityState(it.def, it.cooldown) },
        equipment.toMutableList(), isKing, isToken, broodLeft,
    )
}

class PlayerState(
    val index: Int,
    val name: String,
    val deck: MutableList<CardInstance>,
    val hand: MutableList<CardInstance> = mutableListOf(),
    /** Used cards that come back: shuffled into a new deck when the deck runs out. */
    val discard: MutableList<CardInstance> = mutableListOf(),
    /** Cards used up for good: played units, equipment, damage and summoning spells. */
    val exhausted: MutableList<CardInstance> = mutableListOf(),
    var deployed: Int = 0,
    var deployDone: Boolean = false,
    /** Racial trait from the race of this player's King (null: none, used by tests). */
    var trait: RacialTrait? = null,
) {
    fun copy() = PlayerState(index, name, deck.toMutableList(), hand.toMutableList(), discard.toMutableList(), exhausted.toMutableList(), deployed, deployDone, trait)
}

class StackItem(
    val id: Int,
    val controller: Int,
    val action: Action,
    val label: String,
    val card: CardInstance? = null,
    var countered: Boolean = false,
) {
    fun copy() = StackItem(id, controller, action, label, card, countered)
}

/** The battlefield's field (from a Strategy card): helps [owner] until another Strategy replaces it. */
class FieldEffect(val owner: Int, val rule: FieldRule, val cardId: String) {
    fun copy() = FieldEffect(owner, rule, cardId)
}

enum class Phase { DEPLOY, BATTLE, GAME_OVER }

/** Small deterministic RNG whose state can be copied along with the game. */
class Rng(var seed: Long) {
    fun nextInt(bound: Int): Int {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return ((seed ushr 33) % bound).toInt()
    }

    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }
}

/** Visual events for the UI (floating numbers, flashes). Ordered by [seq]. */
sealed interface GameEvent {
    val seq: Int
    data class Damaged(override val seq: Int, val unitId: Int, val pos: Pos, val amount: Int) : GameEvent
    data class Healed(override val seq: Int, val unitId: Int, val pos: Pos, val amount: Int) : GameEvent
    data class Died(override val seq: Int, val unitId: Int, val pos: Pos) : GameEvent
    data class Moved(override val seq: Int, val unitId: Int, val from: Pos, val to: Pos) : GameEvent
    data class Status(override val seq: Int, val pos: Pos, val text: String) : GameEvent
    data class Announce(override val seq: Int, val player: Int, val text: String) : GameEvent
}

class GameState(
    val players: List<PlayerState>,
    val units: MutableList<UnitState>,
    var phase: Phase,
    var activePlayer: Int,
    val firstPlayer: Int,
    var turnNumber: Int = 0,
    val stack: MutableList<StackItem> = mutableListOf(),
    /** Player who may currently respond to the stack, or null when no response window is open. */
    var priority: Int? = null,
    val fields: MutableList<FieldEffect> = mutableListOf(),
    var winner: Int? = null,
    var isDraw: Boolean = false,
    var blitzUsed: Boolean = false,
    var nextId: Int = 1,
    val rng: Rng,
    val log: MutableList<String> = mutableListOf(),
    val events: MutableList<GameEvent> = mutableListOf(),
    var eventSeq: Int = 0,
    /** True for throwaway copies used by the AI: logging and events are skipped. */
    val simulation: Boolean = false,
) {
    fun unit(id: Int): UnitState? = units.firstOrNull { it.id == id && it.alive }
    fun unitAt(p: Pos): UnitState? = units.firstOrNull { it.pos == p && it.alive }
    fun unitsOf(player: Int): List<UnitState> = units.filter { it.owner == player && it.alive }
    fun king(player: Int): UnitState? = units.firstOrNull { it.owner == player && it.isKing && it.alive }
    fun fieldActive(player: Int, rule: FieldRule): Boolean = fields.any { it.owner == player && it.rule == rule }
    fun newId(): Int = nextId++

    fun log(msg: String) {
        if (!simulation) log += msg
    }

    fun event(make: (Int) -> GameEvent) {
        if (!simulation) events += make(++eventSeq)
    }

    fun copyForSimulation(): GameState = GameState(
        players = players.map { it.copy() },
        units = units.mapTo(mutableListOf()) { it.copy() },
        phase = phase,
        activePlayer = activePlayer,
        firstPlayer = firstPlayer,
        turnNumber = turnNumber,
        stack = stack.mapTo(mutableListOf()) { it.copy() },
        priority = priority,
        fields = fields.mapTo(mutableListOf()) { it.copy() },
        winner = winner,
        isDraw = isDraw,
        blitzUsed = blitzUsed,
        nextId = nextId,
        rng = Rng(rng.seed),
        eventSeq = eventSeq,
        simulation = true,
    )
}
