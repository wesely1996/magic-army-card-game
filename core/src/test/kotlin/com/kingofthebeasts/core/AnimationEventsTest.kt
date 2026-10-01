package com.kingofthebeasts.core

import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.CastLook
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameEvent
import com.kingofthebeasts.core.game.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The board animates from these events, so they must say who did what, where and in which order. */
class AnimationEventsTest {
    @Test
    fun aDamageSpellIsCastThenHitsThenKills() {
        val (s, units) = battle(
            Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)), Triple("w_pup", 1, p(4, 4)),
        )
        val pup = units[2]
        val bite = s.giveCard(0, "w_bite")
        val before = s.eventSeq
        GameEngine.apply(s, Action.PlayCard(bite.uid, Target.Unit(pup.id)))
        val events = s.events.filter { it.seq > before }
        val cast = events.filterIsInstance<GameEvent.Cast>().single()
        assertEquals(CastLook.HARM, cast.look)
        assertEquals(p(4, 4), cast.at)
        assertEquals(null, cast.from, "a card from the hand has no casting unit")
        val died = events.filterIsInstance<GameEvent.Died>().single()
        assertEquals("w_pup", died.cardId)
        assertEquals(1, died.owner)
        assertTrue(events.indexOf(cast) < events.indexOfFirst { it is GameEvent.Damaged }, "cast before the hit")
        assertTrue(events.indexOfFirst { it is GameEvent.Damaged } < events.indexOf(died), "hit before the death")
    }

    @Test
    fun attacksAndArrivalsAreReported() {
        val (s, units) = battle(
            Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)),
            Triple("w_pup", 0, p(3, 3)), Triple("w_pup", 1, p(4, 4)),
        )
        val arrivals = s.events.filterIsInstance<GameEvent.Arrived>()
        assertEquals(units.map { it.id }, arrivals.map { it.unitId })
        val before = s.eventSeq
        GameEngine.apply(s, Action.Attack(units[2].id, units[3].id))
        if (s.priority != null) GameEngine.apply(s, Action.Pass)
        val attack = s.events.filter { it.seq > before }.filterIsInstance<GameEvent.Attacked>().first()
        assertEquals(units[2].id, attack.unitId)
        assertEquals(p(3, 3), attack.from)
        assertEquals(p(4, 4), attack.to)
    }

    @Test
    fun simulationsMakeNoEvents() {
        val (s, units) = battle(Triple("w_king_alpha", 0, p(0, 0)), Triple("l_king_pride", 1, p(7, 7)), Triple("w_pup", 1, p(4, 4)))
        val sim = s.copyForSimulation()
        val bite = sim.giveCard(0, "w_bite")
        GameEngine.apply(sim, Action.PlayCard(bite.uid, Target.Unit(units[2].id)))
        assertTrue(sim.events.isEmpty())
    }
}
