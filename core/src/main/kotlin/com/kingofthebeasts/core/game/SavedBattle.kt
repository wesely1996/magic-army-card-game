package com.kingofthebeasts.core.game

import com.kingofthebeasts.core.deck.Deck
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A battle in progress, saved so it can be resumed later. The engine is deterministic, so the
 * decks, the seed and the list of actions taken are enough to rebuild the exact position.
 */
@Serializable
data class SavedBattle(
    val player: Deck,
    val opponent: Deck,
    /** AI difficulty name (see `Difficulty`). */
    val difficulty: String,
    val seed: Long,
    /** Every action taken so far, in order, encoded with [ActionCodec]. */
    val actions: List<String>,
    val turn: Int = 0,
) {
    /** Replays the battle; null if it can't be rebuilt (e.g. the cards changed in an update). */
    fun replay(names: List<String> = listOf("You", "Opponent")): GameState? = runCatching {
        val s = GameEngine.newGame(player, opponent, names, seed)
        for (code in actions) GameEngine.apply(s, ActionCodec.decode(code))
        s
    }.getOrNull()

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(text: String): SavedBattle? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/** Compact text form of an [Action], e.g. "A:12:15" (attack) or "P:7:U:15" (play card on a unit). */
object ActionCodec {
    fun encode(a: Action): String = when (a) {
        is Action.Deploy -> "D:${a.cardUid}:${a.pos.x}:${a.pos.y}"
        Action.EndDeploy -> "E"
        is Action.Move -> "M:${a.unitId}:${a.to.x}:${a.to.y}"
        is Action.Attack -> "A:${a.unitId}:${a.targetId}"
        is Action.UseAbility -> "U:${a.unitId}:${a.abilityIndex}:${target(a.target)}"
        is Action.PlayCard -> "P:${a.cardUid}:${target(a.target)}"
        Action.Pass -> "S"
    }

    fun decode(s: String): Action {
        val p = s.split(":")
        return when (p[0]) {
            "D" -> Action.Deploy(p[1].toInt(), Pos(p[2].toInt(), p[3].toInt()))
            "E" -> Action.EndDeploy
            "M" -> Action.Move(p[1].toInt(), Pos(p[2].toInt(), p[3].toInt()))
            "A" -> Action.Attack(p[1].toInt(), p[2].toInt())
            "U" -> Action.UseAbility(p[1].toInt(), p[2].toInt(), target(p.drop(3)))
            "P" -> Action.PlayCard(p[1].toInt(), target(p.drop(2)))
            "S" -> Action.Pass
            else -> error("Unknown action: $s")
        }
    }

    private fun target(t: Target): String = when (t) {
        Target.None -> "N"
        is Target.Unit -> "U:${t.unitId}"
        is Target.Tile -> "T:${t.pos.x}:${t.pos.y}"
        is Target.StackEntry -> "K:${t.itemId}"
    }

    private fun target(p: List<String>): Target = when (p[0]) {
        "N" -> Target.None
        "U" -> Target.Unit(p[1].toInt())
        "T" -> Target.Tile(Pos(p[1].toInt(), p[2].toInt()))
        "K" -> Target.StackEntry(p[1].toInt())
        else -> error("Unknown target: $p")
    }
}
