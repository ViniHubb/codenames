package com.resenha.host.server.sintonia

import com.resenha.host.server.GameStatus
import com.resenha.host.server.Team
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

/** A spectrum card: two opposite concepts sitting at the ends of the dial. */
@Serializable
data class Spectrum(val left: String, val right: String)

/** The four beats of a round. The host drives every transition. */
enum class SintoniaPhase { CLUE, GUESS, BET, REVEAL }

/** Which side of the pointer the betting team thinks the hidden target is on. */
enum class Side {
    LEFT, RIGHT;

    companion object {
        fun from(raw: String?): Side? = when (raw?.uppercase()) {
            "LEFT" -> LEFT
            "RIGHT" -> RIGHT
            else -> null
        }
    }
}

/**
 * Authoritative Sintonia (Wavelength) rules. Pure Kotlin so it can be unit tested; every
 * mutation bumps [version].
 *
 * The dial is a 0..100 axis hiding a [target]. The master gives a clue, enters the team's answer
 * as [pointer], then the other team bets which [Side] of it the target sits on. Scoring: 4/3/2
 * point bands around the target, plus 1 for a correct side bet.
 */
class SintoniaEngine(
    deck: List<Spectrum>,
    private val rng: Random = Random.Default
) {
    // Declared first so the emptiness check runs before `spectrum` reads from it.
    private var deck: List<Spectrum> =
        deck.also { require(it.isNotEmpty()) { "Spectrum deck must not be empty." } }

    var spectrum: Spectrum = this.deck.first()
        private set
    var target: Int = CENTER
        private set
    var clue: String = ""
        private set
    var pointer: Int = CENTER
        private set
    var bet: Side? = null
        private set
    var phase: SintoniaPhase = SintoniaPhase.CLUE
        private set
    var guessingTeam: Team = Team.RED
        private set
    var scoreRed: Int = 0
        private set
    var scoreBlue: Int = 0
        private set
    var round: Int = 1
        private set
    var status: GameStatus = GameStatus.PLAYING
        private set
    var version: Int = 0
        private set

    /** What the guessing team scored last round, for the reveal screen. */
    var lastGuessPoints: Int = 0
        private set

    /** What the betting team scored last round, for the reveal screen. */
    var lastBetPoints: Int = 0
        private set

    /** Cards left to draw. Refilled by reshuffling, so a match rarely repeats a card. */
    private val queue = ArrayDeque<Spectrum>()

    init {
        newGame()
    }

    /** Resets scores and starts a fresh match, optionally from a new [pool]. */
    fun newGame(pool: List<Spectrum> = deck) {
        require(pool.isNotEmpty()) { "Spectrum pool must not be empty." }
        deck = pool
        queue.clear()
        scoreRed = 0
        scoreBlue = 0
        round = 1
        status = GameStatus.PLAYING
        guessingTeam = if (rng.nextBoolean()) Team.RED else Team.BLUE
        startRound()
        version++
    }

    /** Records the master's clue and hands the dial to the guessing team. */
    fun setClue(text: String) {
        if (status != GameStatus.PLAYING || phase != SintoniaPhase.CLUE) return
        clue = text.trim().take(MAX_CLUE_LENGTH)
        phase = SintoniaPhase.GUESS
        version++
    }

    /** Moves the pointer while the team debates. Streamed from the master as they drag. */
    fun setPointer(value: Int) {
        if (status != GameStatus.PLAYING || phase != SintoniaPhase.GUESS) return
        val clamped = value.coerceIn(0, 100)
        if (clamped == pointer) return
        pointer = clamped
        version++
    }

    /** Freezes the team's answer and opens the opposing team's side bet. */
    fun lockGuess() {
        if (status != GameStatus.PLAYING || phase != SintoniaPhase.GUESS) return
        phase = SintoniaPhase.BET
        version++
    }

    /** Records the side bet, scores the round and reveals the target to everyone. */
    fun setBet(side: Side) {
        if (status != GameStatus.PLAYING || phase != SintoniaPhase.BET) return
        bet = side
        score()
        phase = SintoniaPhase.REVEAL
        version++
    }

    /** Swaps the guessing team and deals the next round. No-op once someone has won. */
    fun nextRound() {
        if (status != GameStatus.PLAYING || phase != SintoniaPhase.REVEAL) return
        guessingTeam = guessingTeam.other()
        round++
        startRound()
        version++
    }

    /** Points the guessing team earns for a pointer this far from the target. */
    fun pointsForDistance(distance: Int): Int = when {
        distance <= BULLSEYE_HALF_WIDTH -> 4
        distance <= NEAR_HALF_WIDTH -> 3
        distance <= OUTER_HALF_WIDTH -> 2
        else -> 0
    }

    private fun startRound() {
        spectrum = draw()
        // Keep the whole target on the dial so every band stays reachable by the pointer.
        target = rng.nextInt(OUTER_HALF_WIDTH, 100 - OUTER_HALF_WIDTH + 1)
        clue = ""
        pointer = CENTER
        bet = null
        lastGuessPoints = 0
        lastBetPoints = 0
        phase = SintoniaPhase.CLUE
    }

    private fun draw(): Spectrum {
        if (queue.isEmpty()) queue.addAll(deck.distinct().shuffled(rng))
        return queue.removeFirst()
    }

    private fun score() {
        val guessPoints = pointsForDistance(abs(pointer - target))
        // Scores independently of the guess; only an exact hit leaves no side to be right about.
        val betPoints = when {
            target == pointer -> 0
            bet == (if (target > pointer) Side.RIGHT else Side.LEFT) -> 1
            else -> 0
        }

        lastGuessPoints = guessPoints
        lastBetPoints = betPoints
        if (guessingTeam == Team.RED) {
            scoreRed += guessPoints
            scoreBlue += betPoints
        } else {
            scoreBlue += guessPoints
            scoreRed += betPoints
        }

        // A tie at or above the winning score is not a win — the match continues.
        status = when {
            scoreRed >= WINNING_SCORE && scoreRed > scoreBlue -> GameStatus.RED_WINS
            scoreBlue >= WINNING_SCORE && scoreBlue > scoreRed -> GameStatus.BLUE_WINS
            else -> GameStatus.PLAYING
        }
    }

    companion object {
        const val CENTER = 50
        const val WINNING_SCORE = 10
        const val MAX_CLUE_LENGTH = 60

        /** Half-widths of the concentric scoring bands, in dial units (total target = 20 of 100). */
        const val BULLSEYE_HALF_WIDTH = 2
        const val NEAR_HALF_WIDTH = 6
        const val OUTER_HALF_WIDTH = 10
    }
}
