package com.kingofthebeasts.app

import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.SavedBattle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResumeTest {
    /** A battle left mid-match is saved after every action and resumes to the same position. */
    @Test fun aBattleResumesWhereItWasLeft() {
        var saved: SavedBattle? = null
        val vm = GameViewModel(StarterDecks.all[0], StarterDecks.all[1], Difficulty.EASY, 42, onSave = { saved = it })
        repeat(80) {
            if (vm.humanToAct) {
                vm.perform(GameEngine.legalActions(vm.state).firstOrNull { it is Action.Deploy } ?: Action.Pass)
            }
            ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
        assertNotNull("the battle is saved after every action", saved)
        val save = saved!!
        assertTrue("the battle should have moved on", save.actions.size > 10)

        val resumed = GameViewModel(save.player, save.opponent, Difficulty.valueOf(save.difficulty), save.seed, save.actions)
        val before = save.replay()!!
        assertEquals(before.turnNumber, resumed.state.turnNumber)
        assertEquals(before.units.map { Triple(it.id, it.pos, it.hp) }, resumed.state.units.map { Triple(it.id, it.pos, it.hp) })
        assertEquals(resumed.state.eventSeq, resumed.resumedEventSeq)
    }
}
