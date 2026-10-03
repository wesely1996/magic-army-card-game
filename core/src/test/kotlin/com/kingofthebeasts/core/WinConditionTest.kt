package com.kingofthebeasts.core

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.DeckGenerator
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.model.Race
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Whoever loses their King loses the battle, whatever the cards involved. */
class WinConditionTest {
    @Test
    fun theWinnerAlwaysStillHasTheirKing() {
        val kings = CardDatabase.all.filter { it.isKing && it.collectible }
        var selfInflicted = 0
        repeat(120) { g ->
            val rng = Random(g)
            fun deck(): com.kingofthebeasts.core.deck.Deck {
                val king = kings[rng.nextInt(kings.size)]
                val races = (listOf(king.race) + Race.entries.shuffled(rng).filter { it != king.race }.take(rng.nextInt(3)))
                return DeckGenerator.generate(races, king.id, rng)
            }
            val s = GameEngine.newGame(deck(), deck(), listOf("A", "B"), g.toLong())
            val ais = listOf(AiPlayer(Difficulty.EASY, g * 2L), AiPlayer(Difficulty.EASY, g * 2L + 1))
            var steps = 0
            while (s.phase != Phase.GAME_OVER) {
                val decision = GameEngine.decision(s)
                val mover = decision.player
                GameEngine.apply(s, ais[mover].choose(s))
                if (s.phase == Phase.BATTLE) {
                    // Nobody plays on without a King.
                    assertNotNull(s.king(0), "game $g: player 0 lost their King but the battle went on")
                    assertNotNull(s.king(1), "game $g: player 1 lost their King but the battle went on")
                }
                if (s.phase == Phase.GAME_OVER && !s.isDraw && s.winner != mover && decision.kind == DecisionKind.MAIN) selfInflicted++
                if (++steps > 6000) fail("game $g did not finish")
            }
            if (s.isDraw) {
                assertNull(s.king(0)); assertNull(s.king(1))
            } else {
                val w = s.winner ?: fail("game $g: over without a winner or a draw")
                assertNotNull(s.king(w), "game $g: the winner has no King")
                assertNull(s.king(1 - w), "game $g: the loser still has their King")
                assertTrue(s.log.any { "King has fallen" in it })
            }
        }
        println("Games lost by the loser's own move: $selfInflicted / 120")
        // A King can still be caught where no move saves him (e.g. a Sentry tower in reach and no square
        // to escape to), so ending that turn "loses". It must stay rare: Beginner never picks a move far
        // worse than its best one (see AiPlayer.BLUNDER_MARGIN).
        assertTrue(selfInflicted <= 3, "$selfInflicted of 120 battles were lost on the loser's own move")
    }
}
