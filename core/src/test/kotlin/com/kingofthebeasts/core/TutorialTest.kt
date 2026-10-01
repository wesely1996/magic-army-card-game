package com.kingofthebeasts.core

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.game.Action
import com.kingofthebeasts.core.game.DecisionKind
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.tutorial.Tutorial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A student who does what each lesson asks gets through every lesson and wins. */
class TutorialTest {
    @Test
    fun aStudentFollowingTheLessonsWins() {
        val s = Tutorial.newGame()
        val ai = AiPlayer(Difficulty.MEDIUM, 1)
        var step = 0
        val seen = mutableSetOf<Int>()
        var actions = 0
        while (s.phase != Phase.GAME_OVER && actions < 600) {
            step = Tutorial.advance(s, step)
            seen += step
            val lesson = Tutorial.steps[step]
            val d = GameEngine.decision(s)
            if (d.player == Tutorial.RIVAL) {
                GameEngine.apply(s, Tutorial.rivalAction(s))
            } else if (lesson.info) {
                step++ // "Got it"
                continue
            } else {
                val allowed = GameEngine.legalActions(s).filter { Tutorial.allowed(lesson, s, it) }
                assertTrue(allowed.isNotEmpty(), "lesson ${lesson.title} must leave something to do (decision ${d.kind})")
                // The last lesson allows everything: play it like a decent player.
                val pick = if (step == Tutorial.steps.lastIndex) ai.choose(s).takeIf { it in allowed } ?: allowed.first()
                else allowed.first { it != Action.Pass || d.kind == DecisionKind.RESPOND }
                GameEngine.apply(s, pick)
            }
            actions++
        }
        assertEquals(Tutorial.YOU, s.winner, "the student should defeat the Moon Howler")
        assertEquals((0..Tutorial.steps.lastIndex).toSet(), seen, "every lesson should be shown")
    }

    @Test
    fun lessonsOnlyAllowWhatTheyTeach() {
        val s = Tutorial.newGame()
        val placeKing = Tutorial.steps[1]
        val allowed = GameEngine.legalActions(s).filter { Tutorial.allowed(placeKing, s, it) }
        assertEquals(1, allowed.size, "only the King on D1")
        assertEquals(Action.Deploy::class, allowed.single()::class)
        assertTrue(GameEngine.legalActions(s).none { Tutorial.allowed(Tutorial.steps[0], s, it) }, "nothing while reading")
    }
}
