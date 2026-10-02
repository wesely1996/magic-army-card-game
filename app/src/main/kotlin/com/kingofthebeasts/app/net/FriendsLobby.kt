package com.kingofthebeasts.app.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingofthebeasts.app.decks.OnlineSaveRepository
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.net.OnlineSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Finding a friend on the same Wi-Fi: host a game and wait, or browse the games nearby and join
 * one (or type its address). Once connected, [session] carries the games. If the connection drops
 * mid-battle the lobby gets it back: the host listens and announces the game again while the guest
 * keeps knocking on the host's last address (or finds it again by name). The battle is saved after
 * every action, so it can also be rejoined later with [rejoin].
 */
class FriendsLobby(private val context: Context, private val scope: CoroutineScope, private val appVersion: String) {
    sealed interface Step {
        data object Idle : Step
        /** Waiting for a friend; they can also join by one of [addresses]. */
        data class Hosting(val name: String, val addresses: List<String>, val port: Int) : Step
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

    /** Picks the saved battle back up: the host opens it again, the guest looks for the host. */
    fun rejoin() {
        val save = saved ?: return
        reset()
        val deck = if (save.isHost) save.start.hostDeck else save.start.guestDeck
        newSession(save.isHost, save.myName, deck, restore = save)
        step = Step.Connected
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
                reconnect = scope.launch { if (s.isHost) relisten(s) else knock(s) }
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
