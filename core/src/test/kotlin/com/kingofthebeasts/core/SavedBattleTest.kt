package com.kingofthebeasts.core

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.ActionCodec
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.SavedBattle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A saved battle (decks, seed, actions) rebuilds exactly the same position. */
class SavedBattleTest {
    @Test
    fun aSavedBattleReplaysToTheSamePosition() {
        val (p, o) = StarterDecks.all[0] to StarterDecks.all[3]
        val s = GameEngine.newGame(p, o, listOf("You", "Opponent"), 99)
        val ai = AiPlayer(Difficulty.EASY, 5)
        val log = mutableListOf<String>()
        var n = 0
        while (s.phase != Phase.GAME_OVER && n++ < 120) {
            val a = ai.choose(s)
            log += ActionCodec.encode(a)
            GameEngine.apply(s, a)
        }
        val saved = SavedBattle(p, o, Difficulty.EASY.name, 99, log, s.turnNumber)
        val restored = assertNotNull(SavedBattle.decode(saved.encode())?.replay())
        assertEquals(s.turnNumber, restored.turnNumber)
        assertEquals(s.log, restored.log)
        assertEquals(s.units.map { Triple(it.id, it.pos, it.hp) }, restored.units.map { Triple(it.id, it.pos, it.hp) })
        assertEquals(s.players.map { p -> p.hand.map { it.cardId } }, restored.players.map { p -> p.hand.map { it.cardId } })
        assertEquals(GameEngine.decision(s), GameEngine.decision(restored))
    }

    @Test
    fun aBrokenSaveIsRejected() {
        val (p, o) = StarterDecks.all[0] to StarterDecks.all[1]
        assertNull(SavedBattle(p, o, "EASY", 1, listOf("A:999:998")).replay())
        assertNull(SavedBattle.decode("not json"))
    }
}
