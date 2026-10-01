package com.kingofthebeasts.core

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Phase
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plays whole AI-vs-AI games to shake out rule crashes and endless games. */
class AiSoakTest {
    private fun play(g: Int, a: AiPlayer, b: AiPlayer): GameState {
        val decks = StarterDecks.all
        val s = GameEngine.newGame(decks[g % decks.size], decks[(g + 1) % decks.size], listOf("A", "B"), g.toLong())
        val ais = listOf(a, b)
        var steps = 0
        while (s.phase != Phase.GAME_OVER) {
            val d = GameEngine.decision(s)
            GameEngine.apply(s, ais[d.player].choose(s)) // validates legality of every AI choice
            assertTrue(++steps < 5000, "game $g did not finish")
        }
        return s
    }

    @Test
    fun easyGamesFinishWithoutErrors() {
        val games = 24
        var decisive = 0
        repeat(games) { g ->
            val s = play(g, AiPlayer(Difficulty.EASY, g * 2L), AiPlayer(Difficulty.EASY, g * 2L + 1))
            if (!s.isDraw) decisive++
            assertEquals(Phase.GAME_OVER, s.phase)
        }
        println("Easy soak: $decisive/$games games ended with a winner")
        assertTrue(decisive >= games / 2, "most AI games should end with a King falling")
    }

    /** Master (with a short thinking time, to keep the test quick) plays legal moves and finishes games. */
    @Test
    fun masterGamesFinish() {
        repeat(2) { g ->
            val s = play(g, AiPlayer(Difficulty.HARD, g.toLong(), thinkMs = 60), AiPlayer(Difficulty.MEDIUM, g + 9L))
            assertEquals(Phase.GAME_OVER, s.phase)
        }
    }

    @Test
    fun mediumBeatsEasy() {
        val games = 32
        val start = System.nanoTime()
        val pool = Executors.newFixedThreadPool(4)
        val results = (0 until games).map { g ->
            pool.submit<Int> {
                // Vary deck pairings and seats; Medium sits in seat 0 on even games, seat 1 on odd ones.
                val decks = StarterDecks.all
                val swap = g % 2 == 1
                val medium = AiPlayer(Difficulty.MEDIUM, g.toLong())
                val easy = AiPlayer(Difficulty.EASY, g + 50L)
                val s = GameEngine.newGame(decks[g % decks.size], decks[(g + 1 + g / decks.size) % decks.size], listOf("A", "B"), 1000L + g)
                val ais = if (swap) listOf(easy, medium) else listOf(medium, easy)
                var steps = 0
                while (s.phase != Phase.GAME_OVER) {
                    GameEngine.apply(s, ais[GameEngine.decision(s).player].choose(s))
                    check(++steps < 5000) { "game $g did not finish" }
                }
                when (s.winner) {
                    null -> 0
                    (if (swap) 1 else 0) -> 1
                    else -> -1
                }
            }
        }.map { it.get() }
        pool.shutdown()
        val won = results.count { it == 1 }
        val lost = results.count { it == -1 }
        println("Medium vs Easy: $won wins, $lost losses, ${games - won - lost} draws in %.1fs".format((System.nanoTime() - start) / 1e9))
        assertTrue(won >= 0.6 * (won + lost), "looking 3 moves ahead should clearly beat the greedy player")
    }
}
