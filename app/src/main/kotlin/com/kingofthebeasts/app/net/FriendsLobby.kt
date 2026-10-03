package com.kingofthebeasts.app.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingofthebeasts.app.decks.OnlineSaveRepository
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.net.OnlineSave
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Finding a friend, on the same Wi-Fi or over the internet.
 *
 * On the same Wi-Fi (or one phone's hotspot): host a game and wait, or browse the games nearby and
 * join one (or type its address). Over the internet: host a game to get a room code at the relay
 * server, and the friend joins with that code. Once connected, [session] carries the games.
 *
 * If the connection drops mid-battle the lobby gets it back. On Wi-Fi the host listens and announces
 * the game again while the guest keeps knocking on the host's last address (or finds it again by
 * name); over the internet both go back to the same room. The battle is saved after every action,
 * so it can also be rejoined later with [rejoin].
 */
class FriendsLobby(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appVersion: String,
    /** The relay server for internet play; blank when none is set up. */
    private val relayUrl: () -> String,
) {
    sealed interface Step {
        data object Idle : Step
        /** Waiting for a friend; they can also join by one of [addresses]. */
        data class Hosting(val name: String, val addresses: List<String>, val port: Int) : Step
        /** Hosting over the internet: waiting for a friend to join with [code]. */
        data class HostingOnline(val code: String) : Step
        data object Browsing : Step
        data class Connecting(val to: String) : Step
        /** Connected (or reconnecting); the session takes it from here. */
        data object Connected : Step
        data class Failed(val reason: String) : Step
    }

    var step by mutableStateOf<Step>(Step.Idle)
        private set
    var session by mutableStateOf<OnlineSession?>(null)
        private set

    private val saves = OnlineSaveRepository(context)
    /** An unfinished online battle on this phone, if any. */
    var saved by mutableStateOf(saves.load()?.takeIf { it.replays() })
        private set

    val browser = LanBrowser(context)
    private var host: LanHost? = null
    private var job: Job? = null
    private var watch: Job? = null
    private var reconnect: Job? = null
    /** Internet play: keeps this phone in the relay room (see [stayInRoom]). */
    private var relay: Job? = null

    private fun keep(battle: OnlineSave?) {
        saves.save(battle)
        saved = battle
    }

    private fun newSession(isHost: Boolean, name: String, deck: Deck, restore: OnlineSave? = null) =
        OnlineSession(isHost, name, deck, appVersion, scope, restore, onSave = ::keep).also { s ->
            session = s
            watch = scope.launch { s.status.collect { onStatus(s, it) } }
        }

    fun host(name: String, deck: Deck) {
        reset()
        val s = newSession(isHost = true, name, deck)
        if (listen(s, name)) step = Step.Hosting(name, localAddresses(), host!!.port)
    }

    /** Opens the game on this phone and hands every friend who connects to [s] until one sticks. */
    private fun listen(s: OnlineSession, name: String): Boolean {
        val h = runCatching { LanHost(context, name) }.getOrElse {
            step = Step.Failed("Couldn't open a game on this phone.")
            return false
        }
        host = h
        h.advertise()
        job = scope.launch {
            while (true) {
                val socket = runCatching { h.accept() }.getOrNull() ?: break
                step = Step.Connected
                s.attach(SocketLink(socket, scope))
            }
        }
        return true
    }

    private fun stopListening() {
        job?.cancel()
        job = null
        host?.close()
        host = null
    }

    fun browse() {
        reset()
        step = Step.Browsing
        browser.start()
    }

    fun join(name: String, deck: Deck, address: String, port: Int, label: String = address) {
        reset()
        step = Step.Connecting(label)
        val s = newSession(isHost = false, name, deck)
        job = scope.launch {
            val socket = runCatching { connectTo(address, port) }.getOrElse {
                step = Step.Failed("Couldn't reach $label. Check that you're both on the same Wi-Fi and the game is still open.")
                session = null
                watch?.cancel()
                return@launch
            }
            s.hostAddress = address
            s.hostPort = port
            step = Step.Connected
            s.attach(SocketLink(socket, scope))
        }
    }

    /** Opens a game at the relay server and waits for a friend to join with its code. */
    fun hostOnline(name: String, deck: Deck) {
        reset()
        val s = newSession(isHost = true, name, deck)
        relay = scope.launch {
            // A clash with a code someone else is using is very unlikely; just try another.
            repeat(5) {
                val code = Relay.newCode()
                s.room = code
                try {
                    stayInRoom(s, code, firstVisit = true)
                    return@launch
                } catch (_: CodeTaken) {
                }
            }
            failed(s, RelayRefused(RelayRefused.TAKEN, "Couldn't open a game on the server. Try again."))
        }
    }

    /** Joins a friend's internet game by its [code]. */
    fun joinOnline(name: String, deck: Deck, code: String) {
        reset()
        val s = newSession(isHost = false, name, deck)
        s.room = code
        relay = scope.launch { stayInRoom(s, code, firstVisit = true) }
    }

    private class CodeTaken : Exception()

    /**
     * Keeps this phone in relay room [code] for as long as the session lasts: connects, hands each new
     * link to the session, and whenever a link closes (the friend dropped, or this phone lost its
     * connection, e.g. in the background while sharing the code) goes back to the room. Only a first
     * visit that the relay turns away, or a server that can't be reached at all, ends it.
     */
    private suspend fun stayInRoom(s: OnlineSession, code: String, firstVisit: Boolean) {
        var fresh = firstVisit
        var misses = 0
        while (s.status.value !is OnlineSession.Status.Ended) {
            if (s.game.value == null) step = if (s.isHost) Step.HostingOnline(code) else Step.Connecting("game $code")
            var inRoom = false
            val link = try {
                RelayLink.connect(relayUrl(), code, s.isHost, fresh, onWaiting = { inRoom = true })
            } catch (e: RelayRefused) {
                when {
                    !fresh || inRoom -> {
                        // Already in the room once: just come back (no internet right now, or the server is busy).
                        fresh = false
                        delay(2000)
                        continue
                    }
                    s.isHost && e.code == RelayRefused.TAKEN -> throw CodeTaken()
                    // The host may be reconnecting just now; give them a moment.
                    !s.isHost && e.code == RelayRefused.NO_GAME && misses++ < 3 -> {
                        delay(2000)
                        continue
                    }
                    else -> return failed(s, e)
                }
            }
            fresh = false
            link.onPeerBack = { scope.launch { s.greetAgain() } }
            if (s.game.value == null) step = Step.Connected
            s.attach(link)
            try {
                link.awaitClosed()
            } finally {
                link.close()
            }
            delay(500)
        }
    }

    private fun failed(s: OnlineSession, e: Throwable) {
        if (e is CancellationException) throw e
        step = Step.Failed(
            when ((e as? RelayRefused)?.code) {
                RelayRefused.NO_GAME -> "No game with that code is waiting. Check the code with your friend; they need to keep the game open."
                RelayRefused.TAKEN -> e.message ?: "That game already has two players."
                else -> e.message ?: "Couldn't reach the game server. Check your internet connection."
            },
        )
        if (session === s) session = null
        watch?.cancel()
    }

    /** Picks the saved battle back up: the host opens it again, the guest looks for the host. */
    fun rejoin() {
        val save = saved ?: return
        reset()
        val deck = if (save.isHost) save.start.hostDeck else save.start.guestDeck
        val s = newSession(save.isHost, save.myName, deck, restore = save)
        step = Step.Connected
        save.room?.let { code -> relay = scope.launch { stayInRoom(s, code, firstVisit = false) } }
    }

    /** Gives up the saved battle (the friend can no longer rejoin it either). */
    fun abandon() {
        keep(null)
    }

    private fun onStatus(s: OnlineSession, status: OnlineSession.Status) {
        when (status) {
            OnlineSession.Status.Playing -> {
                reconnect?.cancel()
                reconnect = null
                if (s.isHost) stopListening()
            }
            OnlineSession.Status.Reconnecting -> if (reconnect?.isActive != true) {
                // Over the internet the relay loop already goes back to the room by itself.
                if (s.room == null) reconnect = scope.launch { if (s.isHost) relisten(s) else knock(s) }
            }
            is OnlineSession.Status.Ended -> {
                reconnect?.cancel()
                stopListening()
                browser.stop()
            }
            else -> {}
        }
    }

    private fun relisten(s: OnlineSession) {
        stopListening()
        listen(s, s.peerName?.let { "${myName(s)} vs $it" } ?: myName(s))
    }

    private fun myName(s: OnlineSession): String = saved?.myName ?: "King of the Beasts"

    /** Guest: keep trying the host's last address; if it changed, find the game by name. */
    private suspend fun knock(s: OnlineSession) {
        browser.start()
        while (s.status.value == OnlineSession.Status.Reconnecting) {
            val addresses = buildList {
                s.hostAddress?.let { add(it to s.hostPort) }
                val peer = s.peerName
                browser.games.value.filter { peer != null && it.name.startsWith(peer) }.forEach { add(it.host to it.port) }
            }.distinct()
            for ((address, port) in addresses) {
                val socket = runCatching { connectTo(address, port) }.getOrNull() ?: continue
                s.hostAddress = address
                s.hostPort = port
                s.attach(SocketLink(socket, scope))
                // Give the hello a moment; if the host turned us down or vanished, try again.
                delay(3000)
                if (s.status.value != OnlineSession.Status.Reconnecting) break
            }
            delay(2500)
        }
        browser.stop()
    }

    /** Leave the battle but keep it saved, to rejoin later. */
    fun leaveForNow() {
        session?.leaveForNow()
        reset(keepSession = false)
    }

    /** Back to the start of the lobby, closing anything open. Leaving a live battle forfeits it. */
    fun reset(keepSession: Boolean = false) {
        reconnect?.cancel()
        reconnect = null
        relay?.cancel()
        relay = null
        stopListening()
        browser.stop()
        if (!keepSession) {
            session?.let { if (it.status.value !is OnlineSession.Status.Ended) it.leave() }
            watch?.cancel()
            session = null
        }
        step = Step.Idle
    }
}
