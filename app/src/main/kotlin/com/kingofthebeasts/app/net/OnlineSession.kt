package com.kingofthebeasts.app.net

import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.deck.DeckRules
import com.kingofthebeasts.core.net.NetMessage
import com.kingofthebeasts.core.net.NetProtocol
import com.kingofthebeasts.core.net.OnlineSave
import com.kingofthebeasts.core.net.Rejoin
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
    /** The battle has ended; nothing is left to rejoin. */
    fun finished() {}
}

/** One game of a session. The host is engine player 0, the guest player 1. */
class OnlineGame(val start: NetMessage.Start, val human: Int, private val session: OnlineSession) : RemoteSide {
    override val opponentName: String get() = if (human == 0) start.guestName else start.hostName
    private val inbox = Channel<NetMessage.Act>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<NetMessage.Act> get() = inbox

    /** Actions already played before this screen opened (a rejoined battle), to replay first. */
    val replay: List<String> get() = session.restoredCodes(start.game)

    internal fun deliver(act: NetMessage.Act) {
        inbox.trySend(act)
    }

    internal fun close() {
        inbox.close()
    }

    override fun send(index: Int, code: String, checksum: Long) {
        session.sendAct(NetMessage.Act(start.game, index, code, checksum))
    }

    override fun finished() {
        session.gameFinished(start.game)
    }
}

/**
 * A connection to one friend, across any number of games (rematches) and any number of dropped and
 * restored connections. Hand it a [Link] with [attach]; it says hello, checks both apps can play
 * together, and the host then starts games. If the link drops mid-battle the session waits in
 * [Status.Reconnecting] for a new link, and both sides then send each other the actions the other
 * missed. Every action is also saved through [onSave], so a battle can be rejoined after a restart
 * by creating the session from that save ([restore]).
 */
class OnlineSession(
    val isHost: Boolean,
    private val name: String,
    private val deck: Deck,
    private val appVersion: String,
    private val scope: CoroutineScope,
    /** A saved battle to rejoin. */
    restore: OnlineSave? = null,
    /** Called with the battle to keep after every action, or null when there's nothing to rejoin. */
    private val onSave: (OnlineSave?) -> Unit = {},
    /** How often to ping, and how long silence may last before the connection counts as dropped. */
    private val pingMs: Long = 4000,
    private val timeoutMs: Long = 20000,
) {
    sealed interface Status {
        /** Not connected yet (hosting and waiting, or connecting). */
        data object Connecting : Status
        /** Connected; checking versions and waiting for the first game. */
        data object Greeting : Status
        /** A game is set up and the connection is up; see [game]. */
        data object Playing : Status
        /** The connection dropped during a battle; waiting for a new one. Nothing is lost. */
        data object Reconnecting : Status
        /** It's over: the friend left, the apps can't play together, or the lobby connection failed. */
        data class Ended(val reason: String) : Status
    }

    private val statusFlow = MutableStateFlow<Status>(if (restore != null) Status.Reconnecting else Status.Connecting)
    val status: StateFlow<Status> = statusFlow

    private val gameFlow = MutableStateFlow<OnlineGame?>(null)
    /** The current game; replaced on a rematch. */
    val game: StateFlow<OnlineGame?> = gameFlow

    private val rematchFlow = MutableStateFlow(RematchState())
    val rematch: StateFlow<RematchState> = rematchFlow

    /** Who asked for another game after the current one. */
    data class RematchState(val mine: Boolean = false, val theirs: Boolean = false)

    /** The friend's name, once known. */
    var peerName: String? = restore?.peerName
        private set

    /** Where the host was reached (guest side), kept in the save for reconnecting. */
    var hostAddress: String? = restore?.hostAddress
    var hostPort: Int = restore?.hostPort ?: 0
    /** The relay room, when playing over the internet; kept in the save for reconnecting. */
    var room: String? = restore?.room

    private var sessionId: Long = restore?.start?.session ?: if (isHost) Random.nextLong() else 0L
    /** Every action of the current game, both players', in order. */
    private val log = mutableListOf<NetMessage.Act>()
    private var restoredGame = -1
    private var restored = emptyList<String>()
    private var gameOver = false
    private var guestHello: NetMessage.Hello? = null

    private var link: Link? = null
    private var lastHeard = System.currentTimeMillis()
    private val jobs = mutableListOf<Job>()

    init {
        if (restore != null) {
            log += restore.acts
            restoredGame = restore.start.game
            restored = restore.acts.map { it.code }
            gameFlow.value = OnlineGame(restore.start, if (isHost) 0 else 1, this)
        }
    }

    internal fun restoredCodes(game: Int): List<String> = if (game == restoredGame) restored else emptyList()

    private fun hello() = NetMessage.Hello(
        NetProtocol.VERSION, appVersion, NetProtocol.cardFingerprint, name, deck,
        rejoin = gameFlow.value?.takeIf { !gameOver }?.let { Rejoin(sessionId, it.start.game, log.size) },
    )

    /** Use [l] as the connection: the first one, or a new one after a drop. */
    fun attach(l: Link) {
        if (statusFlow.value is Status.Ended) {
            l.close()
            return
        }
        jobs.forEach { it.cancel() }
        jobs.clear()
        link?.close()
        link = l
        if (gameFlow.value == null) statusFlow.value = Status.Greeting
        lastHeard = System.currentTimeMillis()
        send(hello())
        jobs += scope.launch {
            for (line in l.lines) {
                lastHeard = System.currentTimeMillis()
                NetProtocol.decode(line)?.let(::receive)
            }
            dropped(l)
        }
        jobs += scope.launch {
            while (true) {
                delay(pingMs)
                if (System.currentTimeMillis() - lastHeard > timeoutMs) {
                    l.close()
                    dropped(l)
                    break
                }
                send(NetMessage.Ping)
            }
        }
    }

    /**
     * The link [l] went quiet or closed. Mid-battle that only means waiting for a new one; over the
     * internet ([room] set) so does a drop before the first battle, since both phones go back to the room.
     */
    private fun dropped(l: Link) {
        if (link !== l || statusFlow.value is Status.Ended) return
        link = null
        val waiting = gameFlow.value == null && room != null
        if (gameFlow.value != null && !gameOver || waiting) statusFlow.value = Status.Reconnecting
        else end("The connection to your friend was lost.")
    }

    /** Say hello again on the current link: the friend came back and missed the first one. */
    fun greetAgain() {
        if (link != null && statusFlow.value !is Status.Ended) send(hello())
    }

    internal fun send(m: NetMessage) {
        link?.send(NetProtocol.encode(m))
    }

    private fun receive(m: NetMessage) {
        when (m) {
            is NetMessage.Hello -> greet(m)
            is NetMessage.Start -> if (!isHost) setGame(m)
            is NetMessage.Act -> {
                val g = gameFlow.value ?: return
                // Only the next action in line: resent actions we already have are skipped.
                if (m.game != g.start.game || m.index != log.size) return
                log += m
                save()
                g.deliver(m)
            }
            is NetMessage.Rematch -> {
                if (m.game != gameFlow.value?.start?.game) return
                rematchFlow.value = rematchFlow.value.copy(theirs = true)
                maybeRematch()
            }
            NetMessage.Leave -> {
                onSave(null)
                end("${peerName ?: "Your friend"} left.")
            }
            NetMessage.Ping -> {}
            is NetMessage.Reject -> {
                // A saved battle this friend can't continue leaves nothing to rejoin.
                if (gameFlow.value != null) onSave(null)
                end(m.reason)
            }
        }
    }

    private fun greet(m: NetMessage.Hello) {
        val problem = NetProtocol.incompatibility(hello(), m)
        if (problem != null) return refuse(problem, final = true)
        val g = gameFlow.value
        if (g != null && !gameOver) {
            // A battle is going on: only the same friend, rejoining the same battle, may go on.
            val r = m.rejoin
            if (r == null && isHost && log.isEmpty() && m.name == g.start.guestName) {
                // The friend lost the connection before the start of the battle reached them: send it again.
                peerName = m.name
                send(g.start)
                statusFlow.value = Status.Playing
                return
            }
            if (r == null || r.session != sessionId || r.game != g.start.game) {
                return refuse("${m.name} isn't rejoining your battle with ${peerName ?: "your friend"}.", final = false)
            }
            peerName = m.name
            statusFlow.value = Status.Playing
            // Send everything they missed while the connection was down.
            for (act in log.drop(r.have)) send(act)
            return
        }
        if (m.rejoin?.have == 0 && g == null && !isHost) {
            // The host started the battle but the start never reached us; it is sent again on our hello.
            peerName = m.name
            return
        }
        if (m.rejoin != null && (g == null || gameOver)) {
            return refuse("$name's app no longer has that battle, so it can't be rejoined.", final = false)
        }
        peerName = m.name
        if (isHost) {
            if (DeckRules.validate(m.deck.withoutUnknownCards()).isNotEmpty()) {
                return refuse("Your friend's deck isn't ready for battle.", final = true)
            }
            if (g == null) startGame(1, m)
        }
    }

    /** Turn the friend down; unless [final], keep waiting for the right one. */
    private fun refuse(reason: String, final: Boolean) {
        send(NetMessage.Reject(reason))
        val l = link
        if (final) end(reason) else link = null
        scope.launch {
            delay(300) // let the reason go out
            l?.close()
        }
    }

    private fun startGame(number: Int, guest: NetMessage.Hello? = guestHello) {
        val g = guest ?: return
        guestHello = g
        val start = NetMessage.Start(number, Random.nextLong(), name, g.name, deck, g.deck.withoutUnknownCards(), sessionId)
        send(start)
        setGame(start)
    }

    private fun setGame(start: NetMessage.Start) {
        if (!isHost) sessionId = start.session
        rematchFlow.value = RematchState()
        gameFlow.value?.close()
        log.clear()
        gameOver = false
        gameFlow.value = OnlineGame(start, if (isHost) 0 else 1, this)
        statusFlow.value = Status.Playing
        save()
    }

    internal fun sendAct(act: NetMessage.Act) {
        if (act.game == gameFlow.value?.start?.game && act.index == log.size) {
            log += act
            save()
        }
        // While disconnected this goes nowhere; it is resent when the friend rejoins.
        send(act)
    }

    internal fun gameFinished(game: Int) {
        if (game != gameFlow.value?.start?.game) return
        gameOver = true
        onSave(null)
    }

    private fun save() {
        val g = gameFlow.value ?: return
        if (!gameOver) onSave(OnlineSave(isHost, g.start, log.toList(), hostAddress, hostPort, room))
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

    /** Leave for good (mid-game this forfeits) and close the connection. */
    fun leave() {
        send(NetMessage.Leave)
        onSave(null)
        end("You left.")
        val l = link
        scope.launch {
            delay(300) // let the goodbye go out
            l?.close()
        }
    }

    /** Stop for now but keep the battle saved, so both can rejoin it later. */
    fun leaveForNow() {
        end("You left the battle for now. Rejoin it from With friends.")
        link?.close()
    }

    private fun end(reason: String) {
        if (statusFlow.value is Status.Ended) return
        statusFlow.value = Status.Ended(reason)
        jobs.forEach { it.cancel() }
    }
}
