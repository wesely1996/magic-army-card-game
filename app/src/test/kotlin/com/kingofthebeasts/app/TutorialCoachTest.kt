package com.kingofthebeasts.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kingofthebeasts.app.game.GameScreen
import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.app.ui.theme.KingOfTheBeastsTheme
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.tutorial.Tutorial
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w730dp-h360dp-land")
class TutorialCoachTest {
    @get:Rule val rule = createComposeRule()

    /** The lesson note is above the deployment pool, so its buttons can be tapped while the pool is open. */
    @Test fun gotItWorksWhileTheDeploymentPoolIsOpen() {
        val vm = GameViewModel(Tutorial.playerDeck, Tutorial.rivalDeck, Difficulty.EASY, 0L, tutorial = true)
        rule.setContent { KingOfTheBeastsTheme { GameScreen(vm, {}, {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("Deployment pool", substring = true).assertExists()
        assertEquals(0, vm.tutorialStep)
        rule.onNodeWithText("Got it").performClick()
        rule.waitForIdle()
        assertEquals(1, vm.tutorialStep)
    }
}
