package com.kingofthebeasts.app.net

import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.net.NetMessage
import com.kingofthebeasts.core.net.NetProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The other player's side of one game, as the battle screen sees it. */
interface RemoteSide {
    val opponentName: String
    /** The opponent's actions, in order. */
    val incoming: ReceiveChannel<NetMessage.Act>
    fun send(index: Int, code: String, checksum: Long)
}

/** One game of a session. The host is engine player 0, the guest player 1. */
class OnlineGame(val start: NetMessage.Start, val human: Int, private val session: OnlineSession) : RemoteSide {
    override val opponentName: String get() = if (human == 0) start.guestName else start.hostName
    private val inbox = Channel<NetMessage.Act>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<NetMessage.Act> get() = inbox

    internal fun deliver(act: NetMessage.Act) {
        inbox.trySend(act)
    }

    internal fun close() {
        inbox.close()
    }

    override fun send(index: Int, code: String, checksum: Long) {
        session.send(NetMessage.Act(start.game, index, code, checksum))
    }
}

/**
 * A connection to one friend, across any number of games (rematches). Hand it a [Link] with
 * [attach]; it says hello, checks both apps can play together, and the host then starts games.
 */
class OnlineSession(
    val isHost: Boolean,
    private val name: String,
    private val deck: Deck,
    private val appVersion: String,
    private val scope: CoroutineScope,
    /** How often to ping, and how long silence may last before the connection counts as lost. */
    private val pingMs: Long = 4000,
    private val timeoutMs: Long = 20000,
) {
    sealed interface Status {
        /** Not connected yet (hosting and waiting, or connecting). */
        data object Connecting : Status
        /** Connected; checking versions and waiting for the first game. */
        data object Greeting : Status
        /** A game is set up; see [game]. */
        data object Playing : Status
        /** It's over: the friend left, the connection dropped, or the apps can't play together. */
        data class Ended(val reason: String) : Status
    }

    private val statusFlow = MutableStateFlow<Status>(Status.Connecting)
    val status: StateFlow<Status> = statusFlow

    private val gameFlow = MutableStateFlow<OnlineGame?>(null)
    /** The current game; replaced on a rematch. */
    val game: StateFlow<OnlineGame?> = gameFlow

    private val rematchFlow = MutableStateFlow(RematchState())
    val rematch: StateFlow<RematchState> = rematchFlow

    /** Who asked for another game after the current one. */
    data class RematchState(val mine: Boolean = false, val theirs: Boolean = false)

    private var link: Link? = null
    private var theirHello: NetMessage.Hello? = null
    private var lastHeard = System.currentTimeMillis()
    private val jobs = mutableListOf<Job>()

    private val hello = NetMessage.Hello(NetProtocol.VERSION, appVersion, NetProtocol.cardFingerprint, name, deck)

    fun attach(l: Link) {
        link = l
        statusFlow.value = Status.Greeting
        lastHeard = System.currentTimeMillis()
        send(hello)
        jobs += scope.launch {
            for (line in l.lines) {
                lastHeard = System.currentTimeMillis()
                NetProtocol.decode(line)?.let(::receive)
            }
            end("The connection to your friend was lost.")
        }
        jobs += scope.launch {
            while (true) {
                delay(pingMs)
                if (System.currentTimeMillis() - lastHeard > timeoutMs) {
                    end("Your friend stopped answering — the connection was lost.")
                    link?.close()
                    break
                }
                send(NetMessage.Ping)
            }
        }
    }

    internal fun send(m: NetMessage) {
        link?.send(NetProtocol.encode(m))
    }

    private fun receive(m: NetMessage) {
        when (m) {
            is NetMessage.Hello -> {
                theirHello = m
                val problem = NetProtocol.incompatibility(hello, m)
                    ?: if (isHost && DeckRules.validate(m.deck.withoutUnknownCards()).isNotEmpty()) "Your friend's deck isn't ready for battle." else null
                if (problem != null) {
                    send(NetMessage.Reject(problem))
                    end(problem)
                } else if (isHost) {
                    startGame(1)
                }
            }
            is NetMessage.Start -> if (!isHost) setGame(m)
            is NetMessage.Act -> gameFlow.value?.takeIf { it.start.game == m.game }?.deliver(m)
            is NetMessage.Rematch -> {
                if (m.game != gameFlow.value?.start?.game) return
                rematchFlow.value = rematchFlow.value.copy(theirs = true)
                maybeRematch()
            }
            NetMessage.Leave -> end("${theirHello?.name ?: "Your friend"} left.")
            NetMessage.Ping -> {}
            is NetMessage.Reject -> end(m.reason)
        }
    }

    private fun startGame(number: Int) {
        val guest = theirHello ?: return
        val start = NetMessage.Start(number, Random.nextLong(), name, guest.name, deck, guest.deck.withoutUnknownCards())
        send(start)
        setGame(start)
    }

    private fun setGame(start: NetMessage.Start) {
        rematchFlow.value = RematchState()
        gameFlow.value?.close()
        gameFlow.value = OnlineGame(start, if (isHost) 0 else 1, this)
        statusFlow.value = Status.Playing
    }

    /** Ask for (or accept) another game with the same decks once this one is over. */
    fun requestRematch() {
        val g = gameFlow.value ?: return
        rematchFlow.value = rematchFlow.value.copy(mine = true)
        send(NetMessage.Rematch(g.start.game))
        maybeRematch()
    }

    private fun maybeRematch() {
        val r = rematchFlow.value
        val g = gameFlow.value ?: return
        if (r.mine && r.theirs && isHost) startGame(g.start.game + 1)
    }

    /** Leave the session (mid-game this forfeits) and close the connection. */
    fun leave() {
        send(NetMessage.Leave)
        end("You left.")
        val l = link
        scope.launch {
            delay(300) // let the goodbye go out
            l?.close()
        }
    }

    private fun end(reason: String) {
        if (statusFlow.value is Status.Ended) return
        statusFlow.value = Status.Ended(reason)
        jobs.forEach { it.cancel() }
    }
}
