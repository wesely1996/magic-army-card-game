package com.kingofthebeasts.core

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Target
import com.kingofthebeasts.core.model.FieldRule
import com.kingofthebeasts.core.model.Keyword
import com.kingofthebeasts.core.model.RacialTrait
import com.kingofthebeasts.core.game.FieldEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** King immunity, racial traits and the Vermin mechanics (Hidden, Backstab, Brood, arrivals). */
class RulesV3Test {
    @Test
    fun everyKingIsImmovable() {
        assertTrue(CardDatabase.all.filter { it.isKing }.all { Keyword.IMMOVABLE in it.unit!!.keywords })
    }

    @Test
    fun kingsTakeNoDamageFromCardsOrAbilities() {
        val (s, u) = battle(
            Triple("b_king_elder", 0, p(3, 3)), Triple("w_scout", 1, p(3, 4)), Triple("l_king_pride", 1, p(7, 7)),
            Triple("h_king_storm", 0, p(0, 0)),
        )
        val sky = s.giveCard(1, "h_skystrike")
        val bite = s.giveCard(1, "w_bite")
        s.activePlayer = 1
        assertFalse(GameEngine.legalActions(s).any { it == Action.PlayCard(bite.uid, Target.Unit(u[0].id)) }, "pure damage can't target a King")
        // Sky Strike on the Scout's neighbour... aimed at an ally-free spot: splash still skips the King.
        val hp = u[0].hp
        s.units.first { it.id == u[1].id }.owner = 0 // make the Scout an enemy of player 1 next to the King
        GameEngine.apply(s, Action.PlayCard(sky.uid, Target.Unit(u[1].id)))
        assertEquals(hp, u[0].hp, "the splash doesn't hurt the King")
    }

    @Test
    fun kingsStillTakeDamageFromAttacks() {
        val (s, u) = battle(Triple("w_direwolf", 0, p(3, 3)), Triple("b_king_warden", 1, p(3, 4)), Triple("w_king_alpha", 0, p(0, 0)))
        val hp = u[1].hp
        GameEngine.apply(s, Action.Attack(u[0].id, u[1].id))
        assertTrue(u[1].hp < hp)
    }

    @Test
    fun hiddenUnitsCanOnlyBeTargetedFromNextToThem() {
        val (s, u) = battle(
            Triple("s_cobra", 0, p(3, 1)), Triple("v_blade", 1, p(3, 4)), Triple("w_king_alpha", 0, p(0, 0)), Triple("v_king_rat", 1, p(7, 7)),
        )
        assertFalse(u[1] in GameEngine.attackTargets(s, u[0]), "range 3 but not next to it")
        val bite = s.giveCard(0, "w_bite")
        assertFalse(GameEngine.legalActions(s).any { it == Action.PlayCard(bite.uid, Target.Unit(u[1].id)) })
        u[0].pos = p(3, 3)
        assertTrue(u[1] in GameEngine.attackTargets(s, u[0]))
        assertTrue(GameEngine.legalActions(s).any { it == Action.PlayCard(bite.uid, Target.Unit(u[1].id)) })
    }

    @Test
    fun backstabHitsHarderFromBehind() {
        // Player 0's home rows are low y: a blade below its target (smaller y) is behind it.
        val (s, u) = battle(Triple("v_blade", 1, p(3, 2)), Triple("l_guard", 0, p(3, 3)), Triple("v_blade", 1, p(4, 4)))
        val behind = GameEngine.attackDamage(s, u[0], u[1])
        val front = GameEngine.attackDamage(s, u[2], u[1])
        assertEquals(front + GameEngine.BACKSTAB_BONUS, behind)
    }

    @Test
    fun broodSpawnsARatOnEachOfTheNextTwoTurns() {
        val (s, u) = battle(Triple("w_king_alpha", 0, p(0, 0)), Triple("v_king_rat", 1, p(7, 7)))
        val card = s.giveCard(0, "v_brood")
        GameEngine.apply(s, Action.PlayCard(card.uid, Target.Tile(p(0, 4))))
        fun rats() = s.unitsOf(0).count { it.def.id == GameEngine.SWARM_RAT }
        assertEquals(0, rats())
        repeat(3) {
            GameEngine.apply(s, Action.Pass) // player 1 skips
            if (s.activePlayer == 0) GameEngine.apply(s, Action.Pass)
        }
        assertEquals(2, rats(), "one rat on each of the next two turns, then no more")
    }

    @Test
    fun packDriverArrivesWithTwoRats() {
        val (s, _) = battle(Triple("w_king_alpha", 0, p(0, 0)), Triple("v_king_rat", 1, p(7, 7)))
        val card = s.giveCard(0, "v_driver")
        GameEngine.apply(s, Action.PlayCard(card.uid, Target.Tile(p(0, 4))))
        assertEquals(2, s.unitsOf(0).count { it.def.id == GameEngine.SWARM_RAT })
    }

    @Test
    fun usedCardsAreExhaustedOrShuffledBack() {
        val (s, u) = battle(Triple("w_scout", 0, p(3, 3)), Triple("l_cub", 1, p(3, 4)), Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)))
        val bite = s.giveCard(0, "w_bite")
        GameEngine.apply(s, Action.PlayCard(bite.uid, Target.Unit(u[1].id)))
        assertTrue(bite in s.players[0].exhausted, "damage spells are used once")
        GameEngine.apply(s, Action.Pass)
        val frenzy = s.giveCard(0, "w_frenzy")
        GameEngine.apply(s, Action.PlayCard(frenzy.uid, Target.Unit(u[0].id)))
        if (s.stack.isNotEmpty()) GameEngine.apply(s, Action.Pass) // the opponent may get a chance to respond
        assertTrue(frenzy in s.players[0].discard, "non-damage magic goes to the discard pile")
        assertFalse(CardDatabase.get("w_scout").returnsToDeck, "unit cards are used once")
        assertFalse(CardDatabase.get("w_hunt").returnsToDeck, "strategy cards are used once")
        assertFalse(CardDatabase.get("v_swarm").returnsToDeck, "summoning spells are used once")
    }

    @Test
    fun anEmptyDeckIsRefilledFromTheDiscardPile() {
        val (s, _) = battle(Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)))
        val ps = s.players[0]
        ps.discard += ps.deck.take(3)
        ps.exhausted += ps.deck.drop(3)
        ps.deck.clear()
        GameEngine.apply(s, Action.Pass)
        GameEngine.apply(s, Action.Pass) // player 0's turn starts: draws from the reshuffled discard pile
        assertEquals(1, ps.hand.size)
        assertEquals(2, ps.deck.size)
        assertTrue(ps.discard.isEmpty())
    }

    @Test
    fun endlessHordeRaisesTheUnitCap() {
        val (s, _) = battle(Triple("v_king_rat", 0, p(0, 0)), Triple("w_king_alpha", 1, p(7, 7)))
        s.players[0].trait = RacialTrait.ENDLESS_HORDE
        assertEquals(GameEngine.HORDE_UNITS_ON_FIELD, GameEngine.unitCap(s, 0))
        assertEquals(GameEngine.MAX_UNITS_ON_FIELD, GameEngine.unitCap(s, 1))
    }

    @Test
    fun traitsComeFromTheKingsRace() {
        val s = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all.first { it.name == "Warren Horde" }, listOf("A", "B"), 3)
        val king0 = StarterDecks.all[0].cards.keys.map { CardDatabase.get(it) }.first { it.isKing }
        assertEquals(king0.race.trait, s.players[0].trait, "the trait of the deck's King's race")
        assertEquals(RacialTrait.ENDLESS_HORDE, s.players[1].trait)
    }

    @Test
    fun royalPrideStrengthensTheKing() {
        val (s, _) = battle()
        s.players[0].trait = RacialTrait.ROYAL_PRIDE
        val king = GameEngine.summon(s, 0, com.kingofthebeasts.core.game.CardInstance(s.newId(), "l_king_pride"), p(0, 0), token = false)
        val guard = GameEngine.summon(s, 0, com.kingofthebeasts.core.game.CardInstance(s.newId(), "l_guard"), p(1, 0), token = false)
        assertEquals(CardDatabase.get("l_king_pride").unit!!.health + 1, king.maxHp)
        assertEquals(CardDatabase.get("l_guard").unit!!.health, guard.maxHp, "other units are unchanged")
    }

    @Test
    fun creepingPlagueHurtsEnemiesNextToYourUnitsButNotKings() {
        val (s, u) = battle(
            Triple("v_tunnel", 0, p(3, 3)), Triple("l_cub", 1, p(3, 4)), Triple("l_king_pride", 1, p(2, 4)),
            Triple("v_king_rat", 0, p(0, 0)),
        )
        s.fields += FieldEffect(0, FieldRule.PLAGUE, "v_plague")
        val cubHp = u[1].hp
        val kingHp = u[2].hp
        GameEngine.apply(s, Action.Pass) // player 1's turn…
        GameEngine.apply(s, Action.Pass) // …then player 0's turn starts: the plague strikes
        assertEquals(cubHp - 1, u[1].hp)
        assertEquals(kingHp, u[2].hp)
    }

    @Test
    fun ratRunSwapsAnEnemyWithYourNearestUnit() {
        val (s, u) = battle(
            Triple("v_blade", 0, p(3, 2)), Triple("l_guard", 1, p(3, 5)), Triple("v_king_rat", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)),
        )
        val run = s.giveCard(0, "v_ratrun")
        GameEngine.apply(s, Action.PlayCard(run.uid, Target.Unit(u[1].id)))
        assertEquals(p(3, 2), u[1].pos)
        assertEquals(p(3, 5), u[0].pos)
    }
}
