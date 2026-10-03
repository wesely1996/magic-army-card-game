package com.kingofthebeasts.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.app.settings.AppSettings
import com.kingofthebeasts.app.settings.MatchHistory
import com.kingofthebeasts.app.ui.ProfileScreen
import com.kingofthebeasts.app.ui.theme.KingOfTheBeastsTheme
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.model.Race
import com.kingofthebeasts.core.profile.MatchRecord
import com.kingofthebeasts.core.profile.MatchResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
class ProfileTest {
    @get:Rule val rule = createComposeRule()

    /** Giving up is recorded once, as a loss, and the history survives a restart. */
    @Test fun aForfeitIsRecordedAndKept() {
        val context = RuntimeEnvironment.getApplication()
        MatchHistory.load(context)
        MatchHistory.clear()
        val vm = GameViewModel(
            StarterDecks.all[0], StarterDecks.all[1], Difficulty.HARD, 3, onFinished = MatchHistory::add,
        )
        vm.recordResult(forfeitBy = vm.human)
        vm.recordResult(forfeitBy = vm.human)
        assertEquals(1, MatchHistory.records.size)
        val r = MatchHistory.records.single()
        assertEquals(MatchResult.LOSS, r.result)
        assertEquals(Difficulty.HARD.name, r.difficulty)
        assertEquals(StarterDecks.all[0].races, r.myRaces)
        MatchHistory.load(context)
        assertEquals(listOf(r), MatchHistory.records)
    }

    @Test fun theProfileShowsWinRatesFavouritesAndHistory() {
        val context = RuntimeEnvironment.getApplication()
        AppSettings.load(context)
        AppSettings.setName("Nikola")
        MatchHistory.load(context)
        MatchHistory.clear()
        fun add(result: MatchResult, level: Difficulty?, king: String, race: Race, friend: String? = null) = MatchHistory.add(
            MatchRecord(System.currentTimeMillis(), result, level?.name, friend, king, listOf(race), "Deck", "v_king_rat", 40),
        )
        add(MatchResult.WIN, Difficulty.EASY, "w_king_alpha", Race.WOLF)
        add(MatchResult.WIN, Difficulty.EASY, "w_king_alpha", Race.WOLF)
        add(MatchResult.LOSS, Difficulty.MEDIUM, "l_king_pride", Race.LION)
        add(MatchResult.WIN, null, "w_king_alpha", Race.WOLF, friend = "Ana")
        rule.setContent { KingOfTheBeastsTheme { ProfileScreen(onBack = {}) } }
        rule.onNodeWithText("4 battles · 3 won · 1 lost").assertExists()
        rule.onNodeWithText("2–0  100%").assertExists()          // vs Beginner
        // vs Pro, and the Lion row in the by-race list
        assertEquals(2, rule.onAllNodesWithText("0–1  0%").fetchSemanticsNodes().size)
        rule.onNodeWithText("Favourite race: Wolf Pack").assertExists()
        rule.onNodeWithText("Favourite King: Alpha Wolf").assertExists()
        rule.onNodeWithText("Victory vs Ana").assertExists()
        rule.onNodeWithText("Defeat vs Pro AI").assertExists()
        rule.onNodeWithText("Nikola").assertExists()
    }
}
