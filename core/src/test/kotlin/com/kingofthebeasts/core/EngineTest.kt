package com.kingofthebeasts.core

import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.Board
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.Target
import com.kingofthebeasts.core.model.FieldRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineTest {

    @Test
    fun deploymentRequiresKingFirstThenFillsThreeRowsAndStartsBattle() {
        val s = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[1], listOf("A", "B"), 42)
        val first = s.activePlayer
        val actions = GameEngine.legalActions(s)
        assertFalse(Action.EndDeploy in actions, "cannot finish before the King is placed")
        val deploys = actions.filterIsInstance<Action.Deploy>()
        assertTrue(deploys.all { d -> s.players[first].deck.first { it.uid == d.cardUid }.def.isKing })
        assertTrue(deploys.all { Board.isDeployZone(first, it.pos) })
        assertEquals(setOf(0, 1, 2).map { if (first == 0) it else 7 - it }.toSet(), deploys.map { it.pos.y }.toSet())

        // Both players deploy until done; turns alternate.
        var guard = 0
        while (s.phase == Phase.DEPLOY && guard++ < 20) {
            val a = GameEngine.legalActions(s).first { it is Action.Deploy }
            GameEngine.apply(s, a)
        }
        assertEquals(Phase.BATTLE, s.phase)
        assertEquals(GameEngine.MAX_DEPLOY, s.unitsOf(0).size)
        assertEquals(GameEngine.MAX_DEPLOY, s.unitsOf(1).size)
        assertTrue(s.units.all { Board.isDeployZone(it.owner, it.pos) })
        assertEquals(GameEngine.OPENING_HAND, s.players[0].hand.size)
        assertEquals(first, s.activePlayer)
    }

    @Test
    fun groundUnitsAreBlockedButFlyersPassOver() {
        val (s, u) = battle(
            Triple("b_grizzly", 0, p(0, 0)), Triple("b_cub", 0, p(1, 0)), Triple("b_cub", 0, p(0, 1)),
            Triple("b_cub", 0, p(1, 1)), Triple("h_sparrow", 0, p(4, 0)),
        )
        assertEquals(emptyList(), GameEngine.reachable(s, u[0]))
        val (s2, u2) = battle(
            Triple("h_sparrow", 0, p(0, 0)), Triple("b_cub", 0, p(1, 0)), Triple("b_cub", 0, p(0, 1)), Triple("b_cub", 0, p(1, 1)),
        )
        assertTrue(p(2, 2) in GameEngine.reachable(s2, u2[0]))
        assertTrue(p(4, 4) in GameEngine.reachable(s2, u2[0]))
        assertFalse(p(5, 5) in GameEngine.reachable(s2, u2[0]))
    }

    @Test
    fun battleUnitsGoOnTheBorderAwayFromEnemies() {
        val (s, _) = battle(Triple("w_king_alpha", 0, p(3, 0)), Triple("l_king_pride", 1, p(0, 4)))
        val tiles = GameEngine.battleDeployTiles(s, 0)
        assertTrue(tiles.all { it.isBorder })
        assertTrue(tiles.none { it.distanceTo(p(0, 4)) < 2 })
        assertFalse(p(0, 3) in tiles)
        assertTrue(p(0, 2) in tiles)
        assertFalse(p(3, 3) in tiles)
        s.fields += com.kingofthebeasts.core.game.FieldEffect(0, FieldRule.AMBUSH, 3, "l_grass")
        assertTrue(p(3, 3) in GameEngine.battleDeployTiles(s, 0))
    }

    @Test
    fun attackKillsAndKingDeathEndsTheGame() {
        val (s, u) = battle(Triple("w_direwolf", 0, p(3, 3)), Triple("h_king_sky", 1, p(3, 4)), Triple("w_king_alpha", 0, p(0, 0)))
        u[1].hp = 3
        GameEngine.apply(s, Action.Attack(u[0].id, u[1].id))
        assertEquals(Phase.GAME_OVER, s.phase)
        assertEquals(0, s.winner)
    }

    @Test
    fun counterChainResolvesLastInFirstOut() {
        val (s, u) = battle(
            Triple("w_hunter", 0, p(3, 3)), Triple("s_viper", 1, p(3, 4)),
            Triple("w_king_alpha", 0, p(0, 0)), Triple("s_king_basilisk", 1, p(7, 7)),
        )
        val hiss = s.giveCard(1, "s_hiss")
        val scatter = s.giveCard(0, "w_scatter")
        GameEngine.apply(s, Action.Attack(u[0].id, u[1].id))
        assertEquals(1, s.priority)
        GameEngine.apply(s, Action.PlayCard(hiss.uid, Target.StackEntry(s.stack.last().id)))
        assertEquals(0, s.priority, "the attacker may counter the counter")
        GameEngine.apply(s, Action.PlayCard(scatter.uid, Target.StackEntry(s.stack.last().id)))
        // Player 1 has nothing left, so the stack resolves on its own.
        assertNull(s.priority)
        assertTrue(s.stack.isEmpty())
        assertEquals(0, u[1].hp.coerceAtLeast(0), "Hiss was cancelled, so the attack went through")
        assertEquals(1, s.activePlayer)
    }

    @Test
    fun stunInterruptMakesTheAttackFizzle() {
        val (s, u) = battle(
            Triple("w_direwolf", 0, p(3, 3)), Triple("b_cub", 1, p(3, 4)),
            Triple("w_king_alpha", 0, p(0, 0)), Triple("b_king_warden", 1, p(7, 7)),
        )
        val hug = s.giveCard(1, "b_hug")
        GameEngine.apply(s, Action.Attack(u[0].id, u[1].id))
        GameEngine.apply(s, Action.PlayCard(hug.uid, Target.Unit(u[0].id)))
        assertEquals(4, u[1].hp)
        assertEquals(1, s.activePlayer)
        assertEquals(0, u[0].stun, "the stun only interrupted the action on its owner's own turn")
    }

    @Test
    fun passingTheResponseLetsTheActionResolve() {
        val (s, u) = battle(
            Triple("w_direwolf", 0, p(3, 3)), Triple("b_cub", 1, p(3, 4)),
            Triple("w_king_alpha", 0, p(0, 0)), Triple("b_king_warden", 1, p(7, 7)),
        )
        s.giveCard(1, "b_hug")
        GameEngine.apply(s, Action.Attack(u[0].id, u[1].id))
        assertEquals(DecisionKind.RESPOND, GameEngine.decision(s).kind)
        GameEngine.apply(s, Action.Pass)
        assertEquals(0, u[1].hp)
    }

    @Test
    fun retaliateAndPoison() {
        val (s, u) = battle(
            Triple("s_viper", 0, p(3, 3)), Triple("b_brawler", 1, p(3, 4)),
            Triple("s_king_naga", 0, p(0, 0)), Triple("b_king_elder", 1, p(7, 7)),
        )
        GameEngine.apply(s, Action.Attack(u[0].id, u[1].id))
        assertFalse(u[0].alive, "3 retaliation damage kills the 3-health Viper")
        // 6 - 2 from the bite, then 1 poison at the start of its owner's turn.
        assertEquals(3, u[1].hp)
        assertEquals(1, u[1].poisonTurns)
    }

    @Test
    fun alphaWolfSummonsAPup() {
        val (s, u) = battle(Triple("w_king_alpha", 0, p(3, 0)), Triple("l_king_pride", 1, p(3, 7)))
        GameEngine.apply(s, Action.UseAbility(u[0].id, 0, Target.Unit(u[0].id)))
        assertEquals(2, s.unitsOf(0).size)
        assertTrue(s.unitsOf(0).any { it.def.id == "w_pup" && it.isToken })
    }

    @Test
    fun nagaEnthrallsWeakEnemies() {
        val (s, u) = battle(
            Triple("s_king_naga", 0, p(3, 3)), Triple("w_pup", 1, p(3, 5)), Triple("w_king_alpha", 1, p(7, 7)),
        )
        GameEngine.apply(s, Action.UseAbility(u[0].id, 0, Target.Unit(u[1].id)))
        assertEquals(0, u[1].owner)
    }

    @Test
    fun lionessPouncesAcrossTheBoard() {
        val (s, u) = battle(
            Triple("l_king_queen", 0, p(0, 0)), Triple("b_grizzly", 1, p(3, 3)), Triple("b_king_elder", 1, p(7, 7)),
            Triple("b_cub", 0, p(1, 1)),
        )
        GameEngine.apply(s, Action.UseAbility(u[0].id, 0, Target.Unit(u[1].id)))
        assertEquals(1, u[0].pos.distanceTo(u[1].pos))
        assertEquals(7 - 2, u[1].hp, "3 attack, -1 Armored")
    }

    @Test
    fun elderBearIsUnstoppable() {
        val (s, u) = battle(Triple("w_direwolf", 0, p(3, 3)), Triple("b_king_elder", 1, p(3, 4)), Triple("w_king_alpha", 0, p(0, 0)))
        u[0].attack = 9
        GameEngine.apply(s, Action.Attack(u[0].id, u[1].id))
        assertEquals(7, u[1].hp)
    }

    @Test
    fun blitzGivesAFreeMove() {
        val (s, u) = battle(Triple("w_scout", 0, p(3, 1)), Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)))
        s.fields += com.kingofthebeasts.core.game.FieldEffect(0, FieldRule.BLITZ, 3, "w_hunt")
        GameEngine.apply(s, Action.Move(u[0].id, p(3, 3)))
        assertEquals(0, s.activePlayer)
        GameEngine.apply(s, Action.Move(u[0].id, p(3, 4)))
        assertEquals(1, s.activePlayer)
    }
}
