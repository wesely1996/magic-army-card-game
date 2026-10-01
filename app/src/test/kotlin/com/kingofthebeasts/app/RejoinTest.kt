package com.kingofthebeasts.app

import com.kingofthebeasts.app.net.Link
import com.kingofthebeasts.app.net.OnlineGame
import com.kingofthebeasts.app.net.OnlineSession
import com.kingofthebeasts.app.net.SocketLink
import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.ActionCodec
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.net.OnlineSave
import com.kingofthebeasts.core.net.checksum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Online battles survive dropped connections and app restarts. */
class RejoinTest {
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)

    /** One player's app: its session, its copy of the game and its "brain". */
    private inner class App(isHost: Boolean, name: String, restore: OnlineSave? = null, seed: Int) {
        var save: OnlineSave? = restore
        val session = OnlineSession(
            isHost, name, StarterDecks.all[if (isHost) 0 else 3], "test", scope,
            restore = restore, onSave = { save = it }, pingMs = 200, timeoutMs = 1500,
        )
        val ai = AiPlayer(Difficulty.EASY, seed.toLong())
        lateinit var game: OnlineGame
        lateinit var state: GameState
        var link: Link? = null

        fun begin(g: OnlineGame) {
            game = g
            state = GameEngine.newGame(g.start.hostDeck, g.start.guestDeck, listOf(g.start.hostName, g.start.guestName), g.start.seed)
            for (code in g.replay) GameEngine.apply(state, ActionCodec.decode(code))
        }

        var played = restore?.acts?.size ?: 0
    }

    private suspend fun connect(host: App, guest: App) = withContext(thread) {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var accepted: Socket? = null
        val t = Thread { accepted = server.accept() }.apply { start() }
        val g = Socket(InetAddress.getLoopbackAddress(), server.localPort)
        t.join()
        server.close()
        host.link = SocketLink(accepted!!, scope).also { host.session.attach(it) }
        guest.link = SocketLink(g, scope).also { guest.session.attach(it) }
    }

    /** One action by whoever decides; the other side receives it unless [offline]. Returns false at the end. */
    private suspend fun step(apps: List<App>, offline: Boolean = false): Boolean {
        val d = GameEngine.decision(apps[0].state)
        if (d.kind == DecisionKind.NONE) return false
        val me = apps[d.player]
        val action = me.ai.choose(me.state)
        GameEngine.apply(me.state, action)
        withContext(thread) { me.game.send(me.played, ActionCodec.encode(action), me.state.checksum()) }
        me.played++
        if (!offline) receive(apps[1 - d.player])
        return true
    }

    private suspend fun receive(other: App) {
        val act = other.game.incoming.receive()
        assertEquals(other.played, act.index)
        GameEngine.apply(other.state, ActionCodec.decode(act.code))
        assertEquals(act.checksum, other.state.checksum())
        other.played++
    }

    @Test
    fun aDroppedConnectionIsRestoredAndMissedMovesArrive() = runBlocking {
        withTimeout(60_000) {
            val host = App(true, "Ana", seed = 1)
            val guest = App(false, "Ben", seed = 2)
            connect(host, guest)
            host.begin(host.session.game.first { it != null }!!)
            guest.begin(guest.session.game.first { it != null }!!)
            val apps = listOf(host, guest)
            repeat(30) { step(apps) }

            // The Wi-Fi drops: both sides notice and wait.
            withContext(thread) { guest.link!!.close() }
            host.session.status.first { it == OnlineSession.Status.Reconnecting }
            guest.session.status.first { it == OnlineSession.Status.Reconnecting }

            // Whoever's turn it is keeps playing; the moves wait in the save.
            val mover = apps[GameEngine.decision(host.state).player]
            val other = apps[1 - GameEngine.decision(host.state).player]
            step(apps, offline = true)
            assertEquals(mover.played, mover.save!!.acts.size)

            // Back online: the missed move arrives, and the battle goes on to the end.
            connect(host, guest)
            host.session.status.first { it == OnlineSession.Status.Playing }
            guest.session.status.first { it == OnlineSession.Status.Playing }
            receive(other)
            while (step(apps)) {}
            assertEquals(host.state.checksum(), guest.state.checksum())
            assertTrue(host.state.winner != null || host.state.isDraw)
        }
        scope.cancel()
    }

    @Test
    fun bothAppsRestartAndRejoinFromTheirSaves() = runBlocking {
        withTimeout(60_000) {
            val host = App(true, "Ana", seed = 3)
            val guest = App(false, "Ben", seed = 4)
            connect(host, guest)
            host.begin(host.session.game.first { it != null }!!)
            guest.begin(guest.session.game.first { it != null }!!)
            repeat(25) { step(listOf(host, guest)) }
            val hostSave = host.save!!
            val guestSave = guest.save!!
            assertEquals(25, hostSave.acts.size)
            withContext(thread) {
                host.link!!.close()
                guest.link!!.close()
            }

            // Both apps start again from what they saved, and find each other.
            val host2 = App(true, "Ana", hostSave, seed = 5)
            val guest2 = App(false, "Ben", guestSave, seed = 6)
            assertEquals(OnlineSession.Status.Reconnecting, host2.session.status.value)
            host2.begin(host2.session.game.value!!)
            guest2.begin(guest2.session.game.value!!)
            assertEquals(host.state.checksum(), host2.state.checksum())
            connect(host2, guest2)
            host2.session.status.first { it == OnlineSession.Status.Playing }
            guest2.session.status.first { it == OnlineSession.Status.Playing }
            val apps = listOf(host2, guest2)
            while (step(apps)) {}
            assertEquals(host2.state.checksum(), guest2.state.checksum())
            // Finished battles leave nothing to rejoin.
            withContext(thread) {
                host2.game.finished()
                guest2.game.finished()
            }
            assertNull(host2.save)
            assertNull(guest2.save)
        }
        scope.cancel()
    }
}
