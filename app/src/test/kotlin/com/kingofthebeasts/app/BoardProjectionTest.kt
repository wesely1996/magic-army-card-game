package com.kingofthebeasts.app

import com.kingofthebeasts.app.game.BoardProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardProjectionTest {
    /** A tap must land on the square that was drawn there, however the board is turned. */
    @Test fun tapsMapBackToTheSameSquareAtAnyAngle() {
        for (angle in listOf(0f, 37f, 90f, 180f, 270f, -45f)) {
            val proj = BoardProjection(1800f, 1200f, angle)
            for (x in 0 until 8) for (y in 0 until 8) {
                val back = proj.unproject(proj.project(x + 0.5f, y + 0.5f))
                assertNotNull(back)
                assertEquals("x at $angle°", x + 0.5f, back!!.x, 1e-3f)
                assertEquals("y at $angle°", y + 0.5f, back.y, 1e-3f)
            }
        }
    }

    /** Turned 180°, the opponent's back row is nearest the viewer (lowest on screen). */
    @Test fun halfTurnPutsTheOpponentInFront() {
        val flat = BoardProjection(1800f, 1200f, 0f)
        val turned = BoardProjection(1800f, 1200f, 180f)
        assertTrue(flat.project(4f, 0.5f).y > flat.project(4f, 7.5f).y)
        assertTrue(turned.project(4f, 7.5f).y > turned.project(4f, 0.5f).y)
    }
}
