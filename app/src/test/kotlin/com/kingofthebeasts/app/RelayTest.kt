package com.kingofthebeasts.app

import com.kingofthebeasts.app.net.FriendsLobby
import com.kingofthebeasts.app.net.OnlineGame
import com.kingofthebeasts.app.net.OnlineSession
import com.kingofthebeasts.app.net.Relay
import com.kingofthebeasts.app.net.RelayLink
import com.kingofthebeasts.app.net.RelayRefused
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
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import kotlin.random.Random

/** Internet play: two apps meet at the relay by room code and play through it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayTest {
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)
    private val relay = FakeRelay()

    @After fun tearDown() {
        scope.cancel()
        relay.close()
    }

    private class Side(val session: OnlineSession, seed: Long) {
        val ai = AiPlayer(Difficulty.EASY, seed)
        lateinit var game: OnlineGame
        lateinit var state: GameState
        var played = 0
        fun begin(g: OnlineGame) {
            game = g
            state = GameEngine.newGame(g.start.hostDeck, g.start.guestDeck, listOf(g.start.hostName, g.start.guestName), g.start.seed)
            played = 0
        }
    }

    /** One action by whoever decides, received by the other. Returns false once the game is over. */
    private suspend fun step(sides: List<Side>): Boolean {
        val d = GameEngine.decision(sides[0].state)
        if (d.kind == DecisionKind.NONE) return false
        val me = sides[d.player]
        val other = sides[1 - d.player]
        val action = me.ai.choose(me.state)
        GameEngine.apply(me.state, action)
        withContext(thread) { me.game.send(me.played, ActionCodec.encode(action), me.state.checksum()) }
        me.played++
        val act = other.game.incoming.receive()
        assertEquals(other.played, act.index)
        GameEngine.apply(other.state, ActionCodec.decode(act.code))
        assertEquals(act.checksum, other.state.checksum())
        other.played++
        return true
    }

    @Test
    fun codesAreEasyToReadAndType() {
        val codes = List(200) { Relay.newCode(Random(it)) }
        assertTrue(codes.all { Relay.isCode(it) })
        assertTrue(codes.none { c -> c.any { it in "01OI" } })
        assertEquals("K7M2Q", Relay.clean(" k7m-2q "))
        assertFalse(Relay.isCode("K7M2"))
    }

    @Test
    fun twoAppsPlayAWholeBattleThroughTheRelay() = playThrough(relay.url, "ABCDE")

    /**
     * The same against the real relay server, when one is given: run `npx wrangler dev` in server/ and
     * set RELAY_URL=http://127.0.0.1:8787 (or the deployed address).
     */
    @Test
    fun twoAppsPlayThroughTheRealServer() {
        val url = System.getenv("RELAY_URL")
        Assume.assumeTrue("RELAY_URL not set", !url.isNullOrBlank())
        playThrough(url!!, Relay.newCode())
    }

    private fun playThrough(url: String, code: String) = runBlocking {
        withTimeout(60_000) {
            val host = Side(OnlineSession(true, "Ana", StarterDecks.all[0], "test", scope), 1)
            val guest = Side(OnlineSession(false, "Ben", StarterDecks.all[3], "test", scope), 2)
            var waited = false
            val hostLink = async { RelayLink.connect(url, code, host = true, fresh = true, onWaiting = { waited = true }) }
            while (!waited) delay(10)
            val guestLink = RelayLink.connect(url, code, host = false, fresh = true)
            withContext(thread) {
                host.session.attach(hostLink.await())
                guest.session.attach(guestLink)
            }
            host.begin(host.session.game.first { it != null }!!)
            guest.begin(guest.session.game.first { it != null }!!)
            assertEquals("Ben", host.game.opponentName)
            val sides = listOf(host, guest)
            while (step(sides)) {}
            assertEquals(host.state.checksum(), guest.state.checksum())
            assertTrue(host.state.winner != null || host.state.isDraw)
        }
    }

    @Test
    fun aWrongCodeOrAMissingServerIsExplained() = runBlocking {
        withTimeout(20_000) {
            try {
                RelayLink.connect(relay.url, "ZZZZZ", host = false, fresh = true)
                fail("joined a game nobody hosts")
            } catch (e: RelayRefused) {
                assertEquals(RelayRefused.NO_GAME, e.code)
            }
            try {
                RelayLink.connect("http://127.0.0.1:9", "ZZZZZ", host = true, fresh = true)
                fail("reached a server that isn't there")
            } catch (e: RelayRefused) {
                assertEquals(RelayRefused.UNREACHABLE, e.code)
            }
        }
    }

    @Test
    fun theLobbyHostsByCodeAndReconnectsAfterADrop() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val hostLobby = withContext(thread) { FriendsLobby(context, scope, "test") { relay.url } }
        val guestLobby = withContext(thread) { FriendsLobby(context, scope, "test") { relay.url } }
        withTimeout(60_000) {
            withContext(thread) { hostLobby.hostOnline("Ana", StarterDecks.all[1]) }
            var hosting: FriendsLobby.Step.HostingOnline? = null
            while (hosting == null) {
                hosting = hostLobby.step as? FriendsLobby.Step.HostingOnline
                delay(10)
            }
            val code = hosting.code
            // A typo is turned away with an explanation…
            withContext(thread) { guestLobby.joinOnline("Ben", StarterDecks.all[2], "QQQQQ") }
            while (guestLobby.step !is FriendsLobby.Step.Failed) delay(10)
            assertTrue((guestLobby.step as FriendsLobby.Step.Failed).reason.contains("No game"))
            // …and the right code starts the battle.
            withContext(thread) { guestLobby.joinOnline("Ben", StarterDecks.all[2], code) }
            val host = hostLobby.session!!
            val guest = guestLobby.session!!
            host.game.first { it != null }
            guest.game.first { it != null }
            host.status.first { it == OnlineSession.Status.Playing }
            assertEquals("Ben", host.peerName)
            assertEquals(code, guest.room)

            // The guest's phone loses its connection: both wait, then find each other in the room again.
            val before = relay.connections
            relay.drop(code, "guest")
            while (relay.connections < before + 2) delay(10) // both came back to the room
            host.status.first { it == OnlineSession.Status.Playing }
            guest.status.first { it == OnlineSession.Status.Playing }

            // And the battle goes on: a move from the host reaches the guest.
            val g = host.game.value!!
            val state = GameEngine.newGame(g.start.hostDeck, g.start.guestDeck, listOf("Ana", "Ben"), g.start.seed)
            val action = GameEngine.legalActions(state).first()
            GameEngine.apply(state, action)
            withContext(thread) { g.send(0, ActionCodec.encode(action), state.checksum()) }
            val act = guest.game.value!!.incoming.receive()
            assertEquals(ActionCodec.encode(action), act.code)
            withContext(thread) {
                hostLobby.reset()
                guestLobby.reset()
            }
        }
    }

    /**
     * The host shares the code and its phone quietly drops the connection meanwhile; the friend joins
     * (paired with the dead connection) and the host comes back: the battle must still start.
     */
    @Test
    fun theHostLosingItsConnectionWhileWaitingStillGetsTheBattle() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val hostLobby = withContext(thread) { FriendsLobby(context, scope, "test") { relay.url } }
        val guestLobby = withContext(thread) { FriendsLobby(context, scope, "test") { relay.url } }
        withTimeout(60_000) {
            withContext(thread) { hostLobby.hostOnline("Ana", StarterDecks.all[1]) }
            var hosting: FriendsLobby.Step.HostingOnline? = null
            while (hosting == null || relay.connections < 1) {
                hosting = hostLobby.step as? FriendsLobby.Step.HostingOnline
                delay(10)
            }
            delay(200) // the host is waiting in the room
            relay.ghost(hosting.code, "host")
            withContext(thread) { guestLobby.joinOnline("Ben", StarterDecks.all[2], hosting.code) }
            val host = hostLobby.session!!
            val guest = guestLobby.session!!
            host.game.first { it != null }
            guest.game.first { it != null }
            guest.status.first { it == OnlineSession.Status.Playing }
            host.status.first { it == OnlineSession.Status.Playing }
            delay(500)
            assertEquals(OnlineSession.Status.Playing, host.status.value)
            assertEquals(OnlineSession.Status.Playing, guest.status.value)
            assertEquals(hosting.code, (hostLobby.step as? FriendsLobby.Step.HostingOnline)?.code ?: hosting.code)
            assertEquals("Ben", host.peerName)
            withContext(thread) {
                hostLobby.reset()
                guestLobby.reset()
            }
        }
    }

    /** Same, but it's the friend's connection that dies right after joining. */
    @Test
    fun theGuestLosingItsConnectionBeforeTheBattleStillGetsIt() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val hostLobby = withContext(thread) { FriendsLobby(context, scope, "test") { relay.url } }
        val guestLobby = withContext(thread) { FriendsLobby(context, scope, "test") { relay.url } }
        withTimeout(60_000) {
            withContext(thread) { hostLobby.hostOnline("Ana", StarterDecks.all[1]) }
            var hosting: FriendsLobby.Step.HostingOnline? = null
            while (hosting == null || relay.connections < 1) {
                hosting = hostLobby.step as? FriendsLobby.Step.HostingOnline
                delay(10)
            }
            delay(200)
            // The host's hello goes to a guest connection that dies before reading it.
            relay.holdMessagesTo(hosting.code, "guest")
            withContext(thread) { guestLobby.joinOnline("Ben", StarterDecks.all[2], hosting.code) }
            while (guestLobby.step != FriendsLobby.Step.Connected) delay(10)
            delay(300)
            relay.ghost(hosting.code, "guest")
            hostLobby.session!!.game.first { it != null }
            guestLobby.session!!.game.first { it != null }
            guestLobby.session!!.status.first { it == OnlineSession.Status.Playing }
            hostLobby.session!!.status.first { it == OnlineSession.Status.Playing }
            delay(500) // nobody turns anybody away afterwards
            assertEquals(OnlineSession.Status.Playing, hostLobby.session!!.status.value)
            assertEquals(OnlineSession.Status.Playing, guestLobby.session!!.status.value)
            withContext(thread) {
                hostLobby.reset()
                guestLobby.reset()
            }
        }
    }
}
