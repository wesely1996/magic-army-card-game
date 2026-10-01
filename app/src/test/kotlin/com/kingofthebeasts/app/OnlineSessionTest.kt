package com.kingofthebeasts.app

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
import com.kingofthebeasts.core.net.checksum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Two apps on one machine, talking over a real socket, play whole games against each other. */
class OnlineSessionTest {
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private class Side(val session: OnlineSession, val ai: AiPlayer) {
        lateinit var game: OnlineGame
        lateinit var state: GameState
        var sent = 0
        fun begin(g: OnlineGame) {
            game = g
            state = GameEngine.newGame(g.start.hostDeck, g.start.guestDeck, listOf(g.start.hostName, g.start.guestName), g.start.seed)
            sent = 0
        }
    }

    @Test
    fun hostAndGuestPlayWholeGamesAndARematch() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + thread)
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val host = Side(OnlineSession(true, "Ana", StarterDecks.all[0], "test", scope), AiPlayer(Difficulty.EASY, 1))
        val guest = Side(OnlineSession(false, "Ben", StarterDecks.all[3], "test", scope), AiPlayer(Difficulty.EASY, 2))
        withContext(thread) {
            val accepted = Thread { host.session.attach(SocketLink(server.accept(), scope)) }.apply { start() }
            guest.session.attach(SocketLink(Socket(InetAddress.getLoopbackAddress(), server.localPort), scope))
            accepted.join()
        }
        withTimeout(60_000) {
            for (round in 1..2) {
                host.begin(host.session.game.first { it != null && it.start.game == round }!!)
                guest.begin(guest.session.game.first { it != null && it.start.game == round }!!)
                assertEquals(0, host.game.human)
                assertEquals(1, guest.game.human)
                assertEquals("Ben", host.game.opponentName)
                assertEquals(host.game.start.seed, guest.game.start.seed)
                playOut(host, guest)
                assertEquals(host.state.winner, guest.state.winner)
                assertEquals(host.state.checksum(), guest.state.checksum())
                if (round == 1) withContext(thread) {
                    host.session.requestRematch()
                    guest.session.requestRematch()
                }
            }
            withContext(thread) { guest.session.leave() }
            val ended = host.session.status.first { it is OnlineSession.Status.Ended } as OnlineSession.Status.Ended
            assertTrue(ended.reason, "Ben" in ended.reason)
        }
        server.close()
        scope.cancel()
    }

    /** Each side picks its own moves and sends them; the other side checks and applies them. */
    private suspend fun playOut(host: Side, guest: Side) {
        val sides = listOf(host, guest)
        var steps = 0
        while (steps++ < 5000) {
            val d = GameEngine.decision(host.state)
            if (d.kind == DecisionKind.NONE) break
            val me = sides[d.player]
            val other = sides[1 - d.player]
            val action = me.ai.choose(me.state)
            GameEngine.apply(me.state, action)
            withContext(thread) { me.game.send(me.sent, ActionCodec.encode(action), me.state.checksum()) }
            me.sent++
            val act = other.game.incoming.receive()
            val received = ActionCodec.decode(act.code)
            assertTrue(GameEngine.isLegal(other.state, received))
            GameEngine.apply(other.state, received)
            assertEquals(act.checksum, other.state.checksum())
            other.sent++
        }
        delay(10)
    }
}
