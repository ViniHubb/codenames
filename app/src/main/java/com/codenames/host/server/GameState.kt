package com.codenames.host.server

import kotlin.random.Random

/** The two competing teams. */
enum class Team {
    RED, BLUE;

    fun other(): Team = if (this == RED) BLUE else RED
}

/** The true identity of a card on the board. */
enum class CardColor { RED, BLUE, NEUTRAL, ASSASSIN }

enum class GameStatus { PLAYING, RED_WINS, BLUE_WINS }

/** A single board card. [color] is secret until [revealed]. */
data class Card(
    val word: String,
    val color: CardColor,
    var revealed: Boolean = false
)

/**
 * Authoritative, in-memory game state and rules. Pure Kotlin (no Android deps) so it can be
 * unit tested. All mutations bump [version] so clients can detect changes.
 *
 * Classic Codenames distribution over 25 cards: starting team 9, other team 8, 7 neutral,
 * 1 assassin (9 + 8 + 7 + 1 = 25).
 */
class GameEngine(
    private var words: List<String>,
    private val rng: Random = Random.Default
) {
    var cards: List<Card> = emptyList()
        private set
    var startingTeam: Team = Team.RED
        private set
    var currentTurn: Team = Team.RED
        private set
    var status: GameStatus = GameStatus.PLAYING
        private set
    var version: Int = 0
        private set

    init {
        require(words.size >= BOARD_SIZE) {
            "Word bank must contain at least $BOARD_SIZE words (has ${words.size})."
        }
        newGame()
    }

    /** Deals a fresh board (optionally from a new word [pool]) and resets turn/status. */
    fun newGame(pool: List<String> = words) {
        words = pool
        val chosen = pool.distinct().shuffled(rng).take(BOARD_SIZE)
        require(chosen.size >= BOARD_SIZE) {
            "Word pool must have at least $BOARD_SIZE distinct words (has ${chosen.size})."
        }
        startingTeam = if (rng.nextBoolean()) Team.RED else Team.BLUE
        val colors = buildList {
            repeat(STARTING_TEAM_CARDS) { add(startingTeam.toColor()) }
            repeat(SECOND_TEAM_CARDS) { add(startingTeam.other().toColor()) }
            repeat(NEUTRAL_CARDS) { add(CardColor.NEUTRAL) }
            add(CardColor.ASSASSIN)
        }.shuffled(rng)
        cards = chosen.mapIndexed { i, w -> Card(word = w, color = colors[i]) }
        currentTurn = startingTeam
        status = GameStatus.PLAYING
        version++
    }

    /**
     * Reveals the card at [index] (the guess input on the host device). No-op if the game is
     * over, the index is invalid, or the card is already revealed.
     */
    fun reveal(index: Int) {
        if (status != GameStatus.PLAYING) return
        val card = cards.getOrNull(index) ?: return
        if (card.revealed) return

        card.revealed = true
        version++

        when {
            card.color == CardColor.ASSASSIN -> {
                // The guessing team loses immediately.
                status = if (currentTurn == Team.RED) GameStatus.BLUE_WINS else GameStatus.RED_WINS
            }
            allRevealed(Team.RED) -> status = GameStatus.RED_WINS
            allRevealed(Team.BLUE) -> status = GameStatus.BLUE_WINS
            card.color != currentTurn.toColor() -> {
                // Neutral or opponent card guessed: the turn passes.
                currentTurn = currentTurn.other()
            }
            // else: correct guess, same team keeps playing.
        }
    }

    /** Manually ends the current team's turn (guessing is verbal; a team may stop early). */
    fun passTurn() {
        if (status != GameStatus.PLAYING) return
        currentTurn = currentTurn.other()
        version++
    }

    /** Unrevealed cards still belonging to [team]. Public info in Codenames. */
    fun remaining(team: Team): Int =
        cards.count { it.color == team.toColor() && !it.revealed }

    private fun allRevealed(team: Team): Boolean =
        cards.filter { it.color == team.toColor() }.all { it.revealed }

    companion object {
        const val BOARD_SIZE = 25
        const val STARTING_TEAM_CARDS = 9
        const val SECOND_TEAM_CARDS = 8
        const val NEUTRAL_CARDS = 7
    }
}

private fun Team.toColor(): CardColor = if (this == Team.RED) CardColor.RED else CardColor.BLUE
