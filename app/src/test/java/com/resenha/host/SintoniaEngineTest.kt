package com.resenha.host

import com.resenha.host.server.GameStatus
import com.resenha.host.server.Team
import com.resenha.host.server.sintonia.Side
import com.resenha.host.server.sintonia.SintoniaEngine
import com.resenha.host.server.sintonia.SintoniaPhase
import com.resenha.host.server.sintonia.Spectrum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SintoniaEngineTest {

    private val deck = (1..30).map { Spectrum("Esquerda$it", "Direita$it") }

    private fun engine() = SintoniaEngine(deck, Random(42))

    /** Drives a round to the reveal with the given pointer and bet. */
    private fun SintoniaEngine.playRound(pointer: Int, bet: Side) {
        setClue("dica")
        setPointer(pointer)
        lockGuess()
        setBet(bet)
    }

    /** An end of the dial guaranteed to be outside every band, whatever the target is. */
    private fun SintoniaEngine.farPointer(): Int = if (target > 50) 0 else 100

    private fun SintoniaEngine.sideOfTargetFrom(pointer: Int): Side =
        if (target > pointer) Side.RIGHT else Side.LEFT

    @Test
    fun `round starts on the clue phase with a hidden target on the dial`() {
        val e = engine()
        assertEquals(SintoniaPhase.CLUE, e.phase)
        assertEquals(SintoniaEngine.CENTER, e.pointer)
        assertEquals(1, e.round)
        assertNull(e.bet)
        // The whole target must fit on the dial so every band stays reachable.
        assertTrue(e.target >= SintoniaEngine.OUTER_HALF_WIDTH)
        assertTrue(e.target <= 100 - SintoniaEngine.OUTER_HALF_WIDTH)
    }

    @Test
    fun `scoring bands follow the 4-3-2 rings`() {
        val e = engine()
        assertEquals(4, e.pointsForDistance(0))
        assertEquals(4, e.pointsForDistance(2))
        assertEquals(3, e.pointsForDistance(3))
        assertEquals(3, e.pointsForDistance(6))
        assertEquals(2, e.pointsForDistance(7))
        assertEquals(2, e.pointsForDistance(10))
        assertEquals(0, e.pointsForDistance(11))
        assertEquals(0, e.pointsForDistance(100))
    }

    @Test
    fun `an exact hit scores 4 and leaves no side to bet on`() {
        val e = engine()
        val guessing = e.guessingTeam
        e.playRound(pointer = e.target, bet = Side.LEFT)

        assertEquals(4, e.lastGuessPoints)
        assertEquals(0, e.lastBetPoints) // no side left to bet on
        assertEquals(4, if (guessing == Team.RED) e.scoreRed else e.scoreBlue)
        assertEquals(0, if (guessing == Team.RED) e.scoreBlue else e.scoreRed)
    }

    @Test
    fun `the bet scores even when the guess landed in the top band`() {
        val e = engine()
        val guessing = e.guessingTeam
        // One unit short of the target: still a 4-point guess, but the target is to the RIGHT.
        e.playRound(pointer = e.target - 1, bet = Side.RIGHT)

        assertEquals(4, e.lastGuessPoints)
        assertEquals(1, e.lastBetPoints)
        assertEquals(1, if (guessing == Team.RED) e.scoreBlue else e.scoreRed)
    }

    @Test
    fun `the bet scores independently of how well the team guessed`() {
        // Every distance from the target, on both sides, must still resolve the side bet.
        for (offset in listOf(-30, -11, -7, -3, -1, 1, 3, 7, 11, 30)) {
            val e = engine()
            val pointer = (e.target + offset).coerceIn(0, 100)
            if (pointer == e.target) continue
            val correct = if (e.target > pointer) Side.RIGHT else Side.LEFT
            e.playRound(pointer = pointer, bet = correct)
            assertEquals("offset $offset", 1, e.lastBetPoints)
        }
    }

    @Test
    fun `a correct side bet scores 1 for the other team`() {
        val e = engine()
        val guessing = e.guessingTeam
        val far = e.farPointer()
        e.playRound(pointer = far, bet = e.sideOfTargetFrom(far))

        assertEquals(0, e.lastGuessPoints) // the far end is outside every band
        assertEquals(1, e.lastBetPoints)
        assertEquals(1, if (guessing == Team.RED) e.scoreBlue else e.scoreRed)
    }

    @Test
    fun `a wrong side bet scores nothing`() {
        val e = engine()
        val far = e.farPointer()
        val wrong = if (e.sideOfTargetFrom(far) == Side.LEFT) Side.RIGHT else Side.LEFT
        e.playRound(pointer = far, bet = wrong)
        assertEquals(0, e.lastBetPoints)
    }

    @Test
    fun `the guessing team alternates every round`() {
        val e = engine()
        val first = e.guessingTeam
        e.playRound(pointer = 0, bet = Side.LEFT)
        e.nextRound()

        assertEquals(first.other(), e.guessingTeam)
        assertEquals(2, e.round)
        assertEquals(SintoniaPhase.CLUE, e.phase)
        assertEquals(SintoniaEngine.CENTER, e.pointer)
        assertEquals("", e.clue)
        assertNull(e.bet)
    }

    @Test
    fun `reaching the winning score ends the match`() {
        val e = engine()
        val first = e.guessingTeam
        e.runUntilOneTeamWins(first)

        val expected = if (first == Team.RED) GameStatus.RED_WINS else GameStatus.BLUE_WINS
        assertEquals(expected, e.status)
        assertTrue((if (first == Team.RED) e.scoreRed else e.scoreBlue) >= SintoniaEngine.WINNING_SCORE)
        assertEquals(0, if (first == Team.RED) e.scoreBlue else e.scoreRed)
    }

    /** Plays until [winner] alone reaches the winning score: bullseyes on its turns, misses and
     *  wrong bets on the other team's, so nobody else scores. */
    private fun SintoniaEngine.runUntilOneTeamWins(winner: Team) {
        repeat(20) {
            if (status != GameStatus.PLAYING) return@repeat
            if (guessingTeam == winner) {
                playRound(pointer = target, bet = Side.LEFT)
            } else {
                val far = farPointer()
                val wrong = if (sideOfTargetFrom(far) == Side.LEFT) Side.RIGHT else Side.LEFT
                playRound(pointer = far, bet = wrong)
            }
            nextRound()
        }
    }

    @Test
    fun `next round is a no-op once the match is over`() {
        val e = engine()
        e.runUntilOneTeamWins(e.guessingTeam)
        val frozenRound = e.round
        val frozenStatus = e.status

        e.nextRound()
        assertEquals(frozenRound, e.round)
        assertEquals(frozenStatus, e.status)
    }

    @Test
    fun `actions outside their phase are ignored`() {
        val e = engine()

        // Still on CLUE: the dial and the bet are not open yet.
        e.setPointer(90)
        assertEquals(SintoniaEngine.CENTER, e.pointer)
        e.setBet(Side.LEFT)
        assertNull(e.bet)
        e.nextRound()
        assertEquals(1, e.round)

        e.setClue("dica")
        assertEquals(SintoniaPhase.GUESS, e.phase)
        // A second clue must not reopen the round.
        e.setClue("outra")
        assertEquals("dica", e.clue)

        e.setPointer(90)
        assertEquals(90, e.pointer)
        e.lockGuess()
        assertEquals(SintoniaPhase.BET, e.phase)

        // The pointer is frozen once the bet is open.
        e.setPointer(10)
        assertEquals(90, e.pointer)
    }

    @Test
    fun `pointer is clamped to the dial`() {
        val e = engine()
        e.setClue("dica")
        e.setPointer(-40)
        assertEquals(0, e.pointer)
        e.setPointer(400)
        assertEquals(100, e.pointer)
    }

    @Test
    fun `new game resets the scoreboard`() {
        val e = engine()
        e.playRound(pointer = e.target, bet = Side.LEFT)
        assertTrue(e.scoreRed + e.scoreBlue > 0)

        e.newGame()
        assertEquals(0, e.scoreRed)
        assertEquals(0, e.scoreBlue)
        assertEquals(1, e.round)
        assertEquals(SintoniaPhase.CLUE, e.phase)
        assertEquals(GameStatus.PLAYING, e.status)
    }

    @Test
    fun `every mutation bumps the version`() {
        val e = engine()
        val start = e.version
        e.setClue("dica")
        val afterClue = e.version
        assertTrue(afterClue > start)
        e.setPointer(30)
        assertTrue(e.version > afterClue)
    }

    @Test
    fun `consecutive rounds draw different cards`() {
        val e = engine()
        val first = e.spectrum
        e.playRound(pointer = 0, bet = Side.LEFT)
        e.nextRound()
        assertNotEquals(first, e.spectrum)
    }
}
