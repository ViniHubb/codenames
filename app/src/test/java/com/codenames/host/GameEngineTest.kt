package com.codenames.host

import com.codenames.host.server.CardColor
import com.codenames.host.server.GameEngine
import com.codenames.host.server.GameStatus
import com.codenames.host.server.Role
import com.codenames.host.server.Team
import com.codenames.host.server.snapshotFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    private val words = (1..40).map { "PALAVRA$it" }

    private fun engine() = GameEngine(words, Random(42))

    private fun Team.color() = if (this == Team.RED) CardColor.RED else CardColor.BLUE

    @Test
    fun `board has classic distribution`() {
        val e = engine()
        assertEquals(25, e.cards.size)
        assertEquals(1, e.cards.count { it.color == CardColor.ASSASSIN })
        assertEquals(7, e.cards.count { it.color == CardColor.NEUTRAL })
        // Starting team has 9, the other 8.
        assertEquals(9, e.cards.count { it.color == e.startingTeam.color() })
        assertEquals(8, e.cards.count { it.color == e.startingTeam.other().color() })
    }

    @Test
    fun `starting team plays first`() {
        val e = engine()
        assertEquals(e.startingTeam, e.currentTurn)
        assertEquals(GameStatus.PLAYING, e.status)
    }

    @Test
    fun `revealing own card keeps the turn`() {
        val e = engine()
        val before = e.currentTurn
        val ownIndex = e.cards.indexOfFirst { it.color == before.color() }
        val v = e.version
        e.reveal(ownIndex)
        assertEquals(before, e.currentTurn)
        assertTrue(e.cards[ownIndex].revealed)
        assertTrue(e.version > v)
    }

    @Test
    fun `revealing neutral card passes the turn`() {
        val e = engine()
        val before = e.currentTurn
        val neutralIndex = e.cards.indexOfFirst { it.color == CardColor.NEUTRAL }
        e.reveal(neutralIndex)
        assertEquals(before.other(), e.currentTurn)
    }

    @Test
    fun `revealing the assassin loses immediately for the current team`() {
        val e = engine()
        val current = e.currentTurn
        val assassinIndex = e.cards.indexOfFirst { it.color == CardColor.ASSASSIN }
        e.reveal(assassinIndex)
        val expected = if (current == Team.RED) GameStatus.BLUE_WINS else GameStatus.RED_WINS
        assertEquals(expected, e.status)
    }

    @Test
    fun `revealing all of a team's cards wins the game`() {
        val e = engine()
        val team = e.startingTeam
        e.cards.withIndex()
            .filter { it.value.color == team.color() }
            .forEach { e.reveal(it.index) }
        val expected = if (team == Team.RED) GameStatus.RED_WINS else GameStatus.BLUE_WINS
        assertEquals(expected, e.status)
    }

    @Test
    fun `reveal is a no-op after the game ends`() {
        val e = engine()
        val assassinIndex = e.cards.indexOfFirst { it.color == CardColor.ASSASSIN }
        e.reveal(assassinIndex)
        val frozen = e.status
        val anyUnrevealed = e.cards.indexOfFirst { !it.revealed }
        e.reveal(anyUnrevealed)
        assertEquals(frozen, e.status)
        assertTrue(!e.cards[anyUnrevealed].revealed)
    }

    @Test
    fun `agent snapshot hides unrevealed colors but spymaster sees all`() {
        val e = engine()
        // Reveal one card so we can verify revealed colors are always exposed.
        e.reveal(0)

        val agent = e.snapshotFor(Role.AGENT)
        agent.cards.forEachIndexed { i, c ->
            if (c.revealed) assertNotNull(c.color) else assertNull(c.color)
        }

        val spymaster = e.snapshotFor(Role.SPYMASTER)
        spymaster.cards.forEach { assertNotNull(it.color) }

        val host = e.snapshotFor(Role.HOST)
        host.cards.forEach { assertNotNull(it.color) }
        assertTrue(host.canReveal)
        assertTrue(!agent.canReveal)
    }
}
