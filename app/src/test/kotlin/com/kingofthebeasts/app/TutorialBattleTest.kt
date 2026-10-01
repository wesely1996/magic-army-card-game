package com.kingofthebeasts.app

import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.tutorial.Tutorial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialBattleTest {
    /** Through the screen's view model: lessons limit the moves, the scripted rival answers, and you win. */
    @Test
    fun theTutorialCanBeFinished() {
        val vm = GameViewModel(Tutorial.playerDeck, Tutorial.rivalDeck, Difficulty.EASY, 0L, tutorial = true)
        val ai = AiPlayer(Difficulty.MEDIUM, 9)
        val lessons = mutableSetOf<Int>()
        repeat(800) {
            if (vm.state.phase == Phase.GAME_OVER) return@repeat
            lessons += vm.tutorialStep
            val lesson = vm.lesson!!
            when {
                lesson.info -> vm.acknowledgeLesson()
                vm.humanToAct -> {
                    val legal = vm.legalActions()
                    assertTrue("“${lesson.title}” must allow something", legal.isNotEmpty())
                    val pick = if (vm.tutorialStep == Tutorial.steps.lastIndex) ai.choose(vm.state).takeIf { it in legal } ?: legal.first()
                    else legal.firstOrNull { it != Action.Pass } ?: legal.first()
                    vm.perform(pick)
                }
                else -> {
                    ShadowLooper.idleMainLooper(1200, TimeUnit.MILLISECONDS)
                    Thread.sleep(2)
                }
            }
        }
        assertEquals(Phase.GAME_OVER, vm.state.phase)
        assertEquals(Tutorial.YOU, vm.state.winner)
        assertEquals(Tutorial.steps.indices.toSet(), lessons)
    }
}
