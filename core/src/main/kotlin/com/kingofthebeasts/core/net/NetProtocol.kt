package com.kingofthebeasts.core.net

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.game.GameState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Messages between two players' apps. Both run the same deterministic engine: the host picks
 * the seed and the decks, then each side sends only its own actions. A checksum of the game
 * state after every action catches the two copies drifting apart.
 *
 * The host is engine player 0 and the guest engine player 1. Every message is one line of JSON.
 */
@Serializable
sealed interface NetMessage {
    /**
     * First message from both sides on every connection. Games only start when [protocol] and [cards]
     * match. [rejoin] is set when the sender already has a battle with this friend and wants to go on.
     */
    @Serializable @SerialName("hello")
    data class Hello(
        val protocol: Int, val appVersion: String, val cards: Long, val name: String, val deck: Deck,
        val rejoin: Rejoin? = null,
    ) : NetMessage

    /** Host → guest: a new game. [game] counts games in this [session] (rematches). */
    @Serializable @SerialName("start")
    data class Start(
        val game: Int, val seed: Long,
        val hostName: String, val guestName: String,
        val hostDeck: Deck, val guestDeck: Deck,
        val session: Long = 0L,
    ) : NetMessage

    /** One action, encoded with ActionCodec. [index] is its place in the game's action list. */
    @Serializable @SerialName("act")
    data class Act(val game: Int, val index: Int, val code: String, val checksum: Long) : NetMessage

    /** Asks for (or agrees to) another game with the same decks. */
    @Serializable @SerialName("rematch")
    data class Rematch(val game: Int) : NetMessage

    /** The sender leaves the session; mid-game this forfeits. */
    @Serializable @SerialName("leave")
    data object Leave : NetMessage

    /** Keeps the connection alive and lets each side notice a silent drop. */
    @Serializable @SerialName("ping")
    data object Ping : NetMessage

    /** The connection is refused, e.g. different game versions. */
    @Serializable @SerialName("reject")
    data class Reject(val reason: String) : NetMessage
}

/** "I have [have] actions of game [game] in session [session]": the other side sends what's missing. */
@Serializable
data class Rejoin(val session: Long, val game: Int, val have: Int)

/**
 * An online battle saved on this phone after every action, so it can be rejoined after a dropped
 * connection or a restart. [acts] are all actions so far, both players', in order.
 */
@Serializable
data class OnlineSave(
    val isHost: Boolean,
    val start: NetMessage.Start,
    val acts: List<NetMessage.Act>,
    /** Where the host was last reached (guest only), to reconnect. */
    val hostAddress: String? = null,
    val hostPort: Int = 0,
    /** The relay room code, for a battle played over the internet. */
    val room: String? = null,
) {
    val peerName: String get() = if (isHost) start.guestName else start.hostName
    val myName: String get() = if (isHost) start.hostName else start.guestName

    /** Whether the saved moves still replay (an update may have changed the rules or cards). */
    fun replays(): Boolean = runCatching {
        val s = com.kingofthebeasts.core.game.GameEngine.newGame(
            start.hostDeck, start.guestDeck, listOf(start.hostName, start.guestName), start.seed,
        )
        for (a in acts) com.kingofthebeasts.core.game.GameEngine.apply(s, com.kingofthebeasts.core.game.ActionCodec.decode(a.code))
    }.isSuccess

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }
        fun decode(text: String): OnlineSave? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

object NetProtocol {
    /** Bump whenever messages or game rules change in a way older versions can't follow. */
    const val VERSION = 3

    /** Service type used to find games on the local network. */
    const val SERVICE_TYPE = "_kingbeasts._tcp."

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }

    fun encode(m: NetMessage): String = json.encodeToString(NetMessage.serializer(), m)

    fun decode(line: String): NetMessage? = runCatching { json.decodeFromString(NetMessage.serializer(), line) }.getOrNull()

    /** Identifies the card list: two apps with different cards or stats can't play together. */
    val cardFingerprint: Long by lazy { fnv(CardDatabase.all.joinToString("\n")) }

    /** Why two hellos can't play together, or null if they can. */
    fun incompatibility(mine: NetMessage.Hello, theirs: NetMessage.Hello): String? = when {
        mine.protocol != theirs.protocol || mine.cards != theirs.cards ->
            "You are on different versions of the game (yours ${mine.appVersion}, theirs ${theirs.appVersion}). Both need the same version."
        else -> null
    }
}

/** 64-bit FNV-1a hash. */
internal fun fnv(text: String): Long {
    var h = -0x340d631b7bdddcdbL
    for (c in text) {
        h = h xor c.code.toLong()
        h *= 0x100000001b3L
    }
    return h
}

/**
 * A fingerprint of everything that matters in the game: units, cards in every pile (in order),
 * the stack, fields, whose decision it is and the random generator. Equal on both phones as long
 * as they agree.
 */
fun GameState.checksum(): Long {
    val b = StringBuilder()
    b.append(phase).append('|').append(activePlayer).append('|').append(turnNumber).append('|')
        .append(priority).append('|').append(winner).append('|').append(isDraw).append('|').append(rng.seed).append('|')
        .append(blitzUsed).append('|').append(nextId).append('|').append(followUp).append('\n')
    for (u in units.sortedBy { it.id }) {
        b.append(u.id).append(',').append(u.card.cardId).append(',').append(u.owner).append(',').append(u.pos)
            .append(',').append(u.hp).append('/').append(u.maxHp).append(',').append(u.attack).append(',').append(u.move)
            .append(',').append(u.range).append(',').append(u.stun).append(',').append(u.poisonDamage).append('x').append(u.poisonTurns)
            .append(',').append(u.shield).append(',').append(u.keywords.map { it.name }.sorted())
            .append(',').append(u.abilities.map { it.cooldown }).append(',').append(u.form)
            .append(',').append(u.turnsAlive).append(',').append(u.kills).append('\n')
    }
    for (p in players) {
        b.append(p.index).append(':').append(p.deck.map { it.uid }).append(p.hand.map { it.uid })
            .append(p.discard.map { it.uid }).append(p.exhausted.map { it.uid }).append(p.deployed).append(p.deployDone).append('\n')
    }
    for (i in stack) b.append(i.id).append(i.controller).append(i.countered).append(';')
    for (f in fields) b.append(f.owner).append(f.rule).append(';')
    return fnv(b.toString())
}
