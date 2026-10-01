package com.kingofthebeasts.app

import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.app.net.RemoteSide
import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.ActionCodec
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.net.NetMessage
import com.kingofthebeasts.core.net.checksum
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineBattleTest {
    private class FakeRemote : RemoteSide {
        override val opponentName = "Ana"
        val inbox = Channel<NetMessage.Act>(Channel.UNLIMITED)
        override val incoming: ReceiveChannel<NetMessage.Act> get() = inbox
        val sent = mutableListOf<NetMessage.Act>()
        override fun send(index: Int, code: String, checksum: Long) {
            sent += NetMessage.Act(1, index, code, checksum)
        }
    }

    /** The guest's screen (player 1) against a host played by the AI on its own copy of the game. */
    @Test
    fun theGuestPlaysAgainstTheHostsMoves() {
        val remote = FakeRemote()
        val vm = GameViewModel(
            StarterDecks.all[0], StarterDecks.all[1], Difficulty.EASY, 77L,
            remote = remote, human = 1, names = listOf("Ana", "Ben"),
        )
        val host = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[1], listOf("Ana", "Ben"), 77L)
        val hostAi = AiPlayer(Difficulty.EASY, 3)
        val guestAi = AiPlayer(Difficulty.EASY, 4)
        var index = 0
        repeat(400) {
            val d = GameEngine.decision(host)
            if (d.kind == DecisionKind.NONE) return@repeat
            if (d.player == 0) {
                val a = hostAi.choose(host)
                GameEngine.apply(host, a)
                remote.inbox.trySend(NetMessage.Act(1, index++, ActionCodec.encode(a), host.checksum()))
                ShadowLooper.idleMainLooper()
            } else {
                assertTrue("the guest should be able to act", vm.humanToAct)
                vm.perform(guestAi.choose(vm.state))
                val act = remote.sent.last()
                assertEquals(index++, act.index)
                GameEngine.apply(host, ActionCodec.decode(act.code))
                assertEquals(host.checksum(), act.checksum)
            }
            assertFalse(vm.desynced)
            assertEquals(host.checksum(), vm.state.checksum())
        }
        assertTrue("both players should have moved", remote.sent.isNotEmpty() && index > remote.sent.size)
    }

    @Test
    fun aTamperedMoveStopsTheBattle() {
        // A seed where the host (the friend) acts first.
        val seed = (1L..50L).first {
            GameEngine.decision(GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[1], listOf("Ana", "Ben"), it)).player == 0
        }
        val remote = FakeRemote()
        val vm = GameViewModel(
            StarterDecks.all[0], StarterDecks.all[1], Difficulty.EASY, seed,
            remote = remote, human = 1, names = listOf("Ana", "Ben"),
        )
        val host = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[1], listOf("Ana", "Ben"), seed)
        val a = GameEngine.legalActions(host).first()
        remote.inbox.trySend(NetMessage.Act(1, 0, ActionCodec.encode(a), 12345L))
        ShadowLooper.idleMainLooper()
        assertTrue("a wrong checksum means the games differ", vm.desynced)
        assertFalse(vm.humanToAct)
    }
}
