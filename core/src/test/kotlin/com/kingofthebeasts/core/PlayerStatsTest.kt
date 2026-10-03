package com.kingofthebeasts.core

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.model.Race
import com.kingofthebeasts.core.profile.MatchRecord
import com.kingofthebeasts.core.profile.MatchResult
import com.kingofthebeasts.core.profile.PlayerStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerStatsTest {
    private fun rec(result: MatchResult, level: Difficulty?, king: String, races: List<Race>, at: Long = 0, friend: String? = null) =
        MatchRecord(at, result, level?.name, friend, king, races, "Deck", "x", 30)

    @Test
    fun winRatesFavouritesAndTheProfileKing() {
        val history = listOf(
            rec(MatchResult.WIN, Difficulty.EASY, "w_king_alpha", listOf(Race.WOLF, Race.LION), at = 1),
            rec(MatchResult.WIN, Difficulty.EASY, "w_king_alpha", listOf(Race.WOLF), at = 2),
            rec(MatchResult.LOSS, Difficulty.MEDIUM, "l_king_pride", listOf(Race.LION), at = 3),
            rec(MatchResult.DRAW, Difficulty.HARD, "l_king_pride", listOf(Race.LION), at = 4),
            rec(MatchResult.LOSS, null, "v_king_rat", listOf(Race.VERMIN), at = 5, friend = "Ana"),
        )
        val s = PlayerStats.of(history)
        assertEquals(5, s.overall.games)
        assertEquals(1.0, s.vsAi.getValue(Difficulty.EASY).winRate)
        assertEquals(0.0, s.vsAi.getValue(Difficulty.MEDIUM).winRate)
        assertEquals(0.5, s.vsAi.getValue(Difficulty.HARD).winRate)
        assertEquals(1, s.vsFriends.losses)
        // Lion was fielded 3 times, Wolf twice.
        assertEquals(Race.LION, s.favoriteRace)
        // Alpha Wolf and the Pride King were both played twice: the Pride King more recently.
        assertEquals("l_king_pride", s.favoriteKing)
        assertNull(PlayerStats.of(emptyList()).favoriteKing)
    }

    @Test
    fun aFinishedBattleIsRecordedFromThePlayersSide() {
        val me = StarterDecks.all[0]
        val them = StarterDecks.all[3]
        val st = GameEngine.newGame(me, them, listOf("You", "Opponent"), 5)
        val ai = listOf(AiPlayer(Difficulty.EASY, 1), AiPlayer(Difficulty.EASY, 2))
        while (st.phase != Phase.GAME_OVER) GameEngine.apply(st, ai[GameEngine.decision(st).player].choose(st))
        val r = MatchRecord.of(st, 0, me, them, Difficulty.MEDIUM, null, at = 99)
        val expected = when {
            st.isDraw -> MatchResult.DRAW
            st.winner == 0 -> MatchResult.WIN
            else -> MatchResult.LOSS
        }
        assertEquals(expected, r.result)
        assertEquals(me.races, r.myRaces)
        assertEquals(st.turnNumber, r.turns)
        assertEquals(me.cards.keys.first { com.kingofthebeasts.core.data.CardDatabase.find(it)!!.isKing }, r.myKing)
        // Giving up mid-battle is a loss; the friend giving up is a win.
        val early = GameEngine.newGame(me, them, listOf("You", "Ana"), 6)
        assertEquals(MatchResult.LOSS, MatchRecord.of(early, 0, me, them, null, "Ana", 1, forfeitBy = 0).result)
        assertEquals(MatchResult.WIN, MatchRecord.of(early, 0, me, them, null, "Ana", 1, forfeitBy = 1).result)
        // Records survive saving.
        assertEquals(listOf(r), MatchRecord.decodeAll(MatchRecord.encodeAll(listOf(r))))
    }
}
