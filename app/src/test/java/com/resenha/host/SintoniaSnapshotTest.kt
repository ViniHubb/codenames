package com.resenha.host

import com.resenha.host.server.Role
import com.resenha.host.server.sintonia.Side
import com.resenha.host.server.sintonia.SintoniaEngine
import com.resenha.host.server.sintonia.Spectrum
import com.resenha.host.server.sintonia.snapshotFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SintoniaSnapshotTest {

    private val deck = (1..30).map { Spectrum("Esquerda$it", "Direita$it") }

    private fun engine() = SintoniaEngine(deck, Random(7))

    @Test
    fun `only the master sees the target before the reveal`() {
        val e = engine()

        assertEquals(e.target, e.snapshotFor(Role.HOST).target)
        // Nobody else may read the target out of the browser dev tools.
        assertNull(e.snapshotFor(Role.VIEWER).target)
        assertNull(e.snapshotFor(Role.AGENT).target)
        assertNull(e.snapshotFor(Role.SPYMASTER).target)
    }

    @Test
    fun `the reveal exposes the target to everyone`() {
        val e = engine()
        e.setClue("dica")
        e.setPointer(40)
        e.lockGuess()
        e.setBet(Side.LEFT)

        assertEquals(e.target, e.snapshotFor(Role.VIEWER).target)
        assertEquals(e.target, e.snapshotFor(Role.HOST).target)
    }

    @Test
    fun `only the master may control the table`() {
        val e = engine()
        assertTrue(e.snapshotFor(Role.HOST).canControl)
        assertFalse(e.snapshotFor(Role.VIEWER).canControl)
        assertFalse(e.snapshotFor(Role.AGENT).canControl)
    }

    @Test
    fun `snapshot carries the game id so clients follow a game switch`() {
        val e = engine()
        val dto = e.snapshotFor(Role.VIEWER)
        assertEquals("sintonia", dto.game)
        assertEquals("state", dto.type)
    }

    @Test
    fun `public round data is always visible`() {
        val e = engine()
        e.setClue("meu ex")
        val dto = e.snapshotFor(Role.VIEWER)

        assertEquals(e.spectrum.left, dto.left)
        assertEquals(e.spectrum.right, dto.right)
        assertEquals("meu ex", dto.clue)
        assertEquals(e.pointer, dto.pointer)
        assertEquals(e.guessingTeam.other().name, dto.bettingTeam)
        assertEquals(SintoniaEngine.WINNING_SCORE, dto.winningScore)
        assertEquals(
            listOf(
                SintoniaEngine.BULLSEYE_HALF_WIDTH,
                SintoniaEngine.NEAR_HALF_WIDTH,
                SintoniaEngine.OUTER_HALF_WIDTH
            ),
            dto.bands
        )
    }
}
