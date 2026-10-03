package com.kingofthebeasts.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.kingofthebeasts.app.game.GameScreen
import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.app.ui.theme.KingOfTheBeastsTheme
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Phase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnouncementTest {
    @get:Rule val rule = createComposeRule()

    private fun count(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    /** Each banner must disappear ~1.8 s after it appears, even though the game keeps changing meanwhile. */
    @Test fun bannersDisappearWhileTheGameKeepsMoving() {
        val vm = GameViewModel(StarterDecks.all[0], StarterDecks.all[1], Difficulty.EASY, 7)
        rule.mainClock.autoAdvance = false
        rule.setContent { KingOfTheBeastsTheme { GameScreen(vm, {}, {}) } }
        val shownFor = mutableMapOf("coin flip" to 0, "Battle!" to 0)
        val versionsWhileShown = mutableMapOf("coin flip" to mutableSetOf<Int>(), "Battle!" to mutableSetOf())
        repeat(120) {
            if (vm.humanToAct && vm.state.phase != Phase.GAME_OVER) {
                // Deploy quickly, then keep skipping turns so the AI acts while banners are up.
                vm.perform(GameEngine.legalActions(vm.state).firstOrNull { it is Action.Deploy } ?: Action.Pass)
            }
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            Thread.sleep(5) // let the AI finish thinking on its background thread
            rule.mainClock.advanceTimeBy(100)
            for (key in shownFor.keys) if (count(key) > 0) {
                shownFor[key] = shownFor.getValue(key) + 100
                versionsWhileShown.getValue(key) += vm.version
            }
        }
        for ((key, ms) in shownFor) {
            assertTrue("'$key' banner was never shown", ms > 0)
            assertTrue("the game must change while '$key' is up for this test to mean anything", versionsWhileShown.getValue(key).size > 1)
            assertTrue("'$key' banner stayed up for $ms ms", ms <= 2500)
        }
    }
}
