package com.kingofthebeasts.core

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.ActionCodec
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.net.NetMessage
import com.kingofthebeasts.core.net.NetProtocol
import com.kingofthebeasts.core.net.checksum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NetProtocolTest {
    @Test
    fun messagesSurviveTheWire() {
        val deck = StarterDecks.all[0]
        val messages = listOf(
            NetMessage.Hello(NetProtocol.VERSION, "0.7.0", NetProtocol.cardFingerprint, "Ana", deck),
            NetMessage.Start(1, 42L, "Ana", "Ben", deck, StarterDecks.all[1]),
            NetMessage.Act(1, 0, "D:5:3:0", 123L),
            NetMessage.Rematch(1),
            NetMessage.Leave,
            NetMessage.Ping,
            NetMessage.Reject("nope"),
        )
        for (m in messages) {
            val line = NetProtocol.encode(m)
            assertEquals(-1, line.indexOf('\n'), "one message per line")
            assertEquals(m, NetProtocol.decode(line))
        }
        assertNull(NetProtocol.decode("not json"))
    }

    @Test
    fun differentCardListsCannotPlay() {
        val deck = StarterDecks.all[0]
        val a = NetMessage.Hello(NetProtocol.VERSION, "0.7.0", NetProtocol.cardFingerprint, "Ana", deck)
        assertNull(NetProtocol.incompatibility(a, a.copy(name = "Ben")))
        assertNotNull(NetProtocol.incompatibility(a, a.copy(cards = a.cards + 1, appVersion = "0.6.9")))
        assertNotNull(NetProtocol.incompatibility(a, a.copy(protocol = a.protocol + 1)))
    }

    /** Two phones, each with its own copy of the game, only swapping actions: they stay in step. */
    @Test
    fun twoCopiesStayInStepBySwappingActions() {
        val host = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[3], listOf("Ana", "Ben"), 99L)
        val guest = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[3], listOf("Ana", "Ben"), 99L)
        val ais = listOf(AiPlayer(Difficulty.EASY, 1), AiPlayer(Difficulty.EASY, 2))
        var actions = 0
        while (actions < 3000) {
            val d = GameEngine.decision(host)
            if (d.kind == DecisionKind.NONE) break
            // The deciding player's own copy picks the action; the other copy receives it as text.
            val (mine, theirs) = if (d.player == 0) host to guest else guest to host
            val action = ais[d.player].choose(mine)
            GameEngine.apply(mine, action)
            val wire = NetProtocol.decode(NetProtocol.encode(NetMessage.Act(1, actions, ActionCodec.encode(action), mine.checksum())))
                as NetMessage.Act
            val received = ActionCodec.decode(wire.code)
            assert(GameEngine.isLegal(theirs, received)) { "the other copy must accept ${wire.code}" }
            GameEngine.apply(theirs, received)
            assertEquals(wire.checksum, theirs.checksum(), "checksums after action $actions")
            actions++
        }
        assertEquals(host.winner, guest.winner)
        assertEquals(DecisionKind.NONE, GameEngine.decision(guest).kind, "the game should finish")
    }

    @Test
    fun theChecksumNoticesAChange() {
        val s = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[1], listOf("A", "B"), 7L)
        val before = s.checksum()
        GameEngine.apply(s, GameEngine.legalActions(s).first())
        assertNotEquals(before, s.checksum())
    }
}
