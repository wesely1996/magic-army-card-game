package com.kingofthebeasts.app.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingofthebeasts.core.deck.Deck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Finding a friend on the same Wi-Fi: host a game and wait, or browse the games nearby and join
 * one (or type its address). Once connected, [session] carries the games.
 */
class FriendsLobby(private val context: Context, private val scope: CoroutineScope, private val appVersion: String) {
    sealed interface Step {
        data object Idle : Step
        /** Waiting for a friend; they can also join by one of [addresses]. */
        data class Hosting(val name: String, val addresses: List<String>, val port: Int) : Step
        data object Browsing : Step
        data class Connecting(val to: String) : Step
        /** Connected; the session takes it from here. */
        data object Connected : Step
        data class Failed(val reason: String) : Step
    }

    var step by mutableStateOf<Step>(Step.Idle)
        private set
    var session by mutableStateOf<OnlineSession?>(null)
        private set

    val browser = LanBrowser(context)
    private var host: LanHost? = null
    private var job: Job? = null

    fun host(name: String, deck: Deck) {
        reset()
        val h = runCatching { LanHost(context, name) }.getOrElse {
            step = Step.Failed("Couldn't open a game on this phone.")
            return
        }
        host = h
        h.advertise()
        step = Step.Hosting(name, localAddresses(), h.port)
        val s = OnlineSession(isHost = true, name, deck, appVersion, scope)
        job = scope.launch {
            val socket = runCatching { h.accept() }.getOrNull() ?: return@launch
            // One friend per game: stop announcing it.
            h.close()
            host = null
            session = s
            step = Step.Connected
            s.attach(SocketLink(socket, scope))
        }
    }

    fun browse() {
        reset()
        step = Step.Browsing
        browser.start()
    }

    fun join(name: String, deck: Deck, address: String, port: Int, label: String = address) {
        reset()
        step = Step.Connecting(label)
        val s = OnlineSession(isHost = false, name, deck, appVersion, scope)
        job = scope.launch {
            val socket = runCatching { connectTo(address, port) }.getOrElse {
                step = Step.Failed("Couldn't reach $label. Check that you're both on the same Wi-Fi and the game is still open.")
                return@launch
            }
            session = s
            step = Step.Connected
            s.attach(SocketLink(socket, scope))
        }
    }

    /** Back to the start of the lobby, closing anything open. */
    fun reset() {
        job?.cancel()
        job = null
        host?.close()
        host = null
        browser.stop()
        session?.let { if (it.status.value !is OnlineSession.Status.Ended) it.leave() }
        session = null
        step = Step.Idle
    }
}
