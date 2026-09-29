package com.magicarmy.core

import com.magicarmy.core.ai.AiPlayer
import com.magicarmy.core.data.StarterDecks
import com.magicarmy.core.game.GameEngine
import com.magicarmy.core.game.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plays whole AI-vs-AI games to shake out rule crashes and endless games. */
class AiSoakTest {
    @Test
    fun aiGamesFinishWithoutErrors() {
        val decks = StarterDecks.all
        var decisive = 0
        val games = 24
        repeat(games) { g ->
            val s = GameEngine.newGame(decks[g % decks.size], decks[(g + 1) % decks.size], listOf("A", "B"), g.toLong())
            val ais = listOf(AiPlayer(g * 2L), AiPlayer(g * 2L + 1))
            var steps = 0
            while (s.phase != Phase.GAME_OVER) {
                val d = GameEngine.decision(s)
                val action = ais[d.player].choose(s)
                GameEngine.apply(s, action) // validates legality of every AI choice
                assertTrue(++steps < 5000, "game $g did not finish")
            }
            if (!s.isDraw) decisive++
            assertEquals(Phase.GAME_OVER, s.phase)
        }
        println("AI soak: $decisive/$games games ended with a winner")
        assertTrue(decisive >= games / 2, "most AI games should end with a King falling")
    }
}
