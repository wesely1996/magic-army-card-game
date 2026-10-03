package com.kingofthebeasts.core

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.UnitState
import kotlin.test.Test
import kotlin.test.assertTrue

/** A playtest position: an enemy wolf stands next to a wounded Rat King who could call more rats instead. */
class AiKingThreatTest {
    private fun position(wolfId: String): Pair<GameState, List<UnitState>> = battle(
        Triple("v_king_rat", 1, p(2, 6)),   // C7, wounded
        Triple(wolfId, 0, p(3, 6)),         // D7, next to him
        Triple("v_rat", 1, p(3, 7)),        // D8, next to the wolf
        Triple("v_rat", 1, p(1, 6)),        // B7
        Triple("w_king_alpha", 0, p(4, 0)),
        active = 1,
    ).also { (s, u) ->
        u[0].hp = 13; u[0].attack = 4
        u[1].attack = 5; u[1].maxHp = 12; u[1].hp = 12
        s.turnNumber = 88
    }

    /** Plays the AI's whole turn (a King may move and then attack). */
    private fun turn(s: GameState, ai: AiPlayer) {
        var guard = 0
        while (GameEngine.decision(s).player == 1 && GameEngine.decision(s).kind != DecisionKind.NONE && guard++ < 4) {
            GameEngine.apply(s, ai.choose(s))
            while (GameEngine.decision(s).player == 0 && GameEngine.decision(s).kind == DecisionKind.RESPOND) GameEngine.apply(s, Action.Pass)
        }
    }

    /** An ordinary wolf: the King's side hits it, or the King walks out of its reach. */
    @Test
    fun everyLevelDealsWithAWolfAtTheKing() {
        for (level in Difficulty.entries) {
            val tries = if (level == Difficulty.EASY) 30 else 4
            var dealt = 0
            repeat(tries) { seed ->
                val (s, u) = position("w_frost")
                val king = u[0]; val wolf = u[1]
                turn(s, AiPlayer(level, seed.toLong(), thinkMs = 300))
                val hurt = !wolf.alive || wolf.hp < 12
                val safe = king.pos.distanceTo(wolf.pos) > GameEngine.rangeOf(s, wolf)
                if (hurt || safe) dealt++
            }
            val needed = if (level == Difficulty.EASY) tries * 0.6 else tries.toDouble()
            assertTrue(dealt >= needed, "$level dealt with the wolf only $dealt/$tries times")
        }
    }

    /** A Retaliate champion strikes back as hard as it hits: the wounded King must not trade blows with it. */
    @Test
    fun theKingDoesNotTradeBlowsWithARetaliatingChampion() {
        for (level in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            repeat(3) { seed ->
                val (s, u) = position("w_packlord")
                val king = u[0]
                turn(s, AiPlayer(level, seed.toLong(), thinkMs = 300))
                assertTrue(king.alive && king.hp == 13, "$level: the King attacked the Packlord and took its retaliation")
            }
        }
    }

    /**
     * A champion with no Retaliate can chase the King anywhere, so running is pointless: the King's side
     * has to fight it rather than call more rats (the playtest complaint).
     */
    @Test
    fun theKingsSideFightsAChampionItCannotOutrun() {
        for (level in Difficulty.entries) {
            val tries = if (level == Difficulty.EASY) 30 else 4
            var fought = 0
            repeat(tries) { seed ->
                val (s, u) = position("w_fenrir")
                val wolf = u[1]
                turn(s, AiPlayer(level, seed.toLong(), thinkMs = 300))
                if (!wolf.alive || wolf.hp < 12) fought++
            }
            val needed = if (level == Difficulty.EASY) tries * 0.6 else tries.toDouble()
            assertTrue(fought >= needed, "$level fought the champion only $fought/$tries times")
        }
    }
}
