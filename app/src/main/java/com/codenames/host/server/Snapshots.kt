package com.codenames.host.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The viewing role a connected client requested. */
enum class Role { HOST, SPYMASTER, AGENT;

    companion object {
        fun from(raw: String?): Role = when (raw?.uppercase()) {
            "HOST" -> HOST
            "SPYMASTER" -> SPYMASTER
            else -> AGENT
        }
    }
}

/** Shared JSON config for both serialization directions. */
val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** A card as seen by a particular client. [color] is null when it must stay secret. */
@Serializable
data class CardDto(
    val word: String,
    val revealed: Boolean,
    val color: String? // null = unknown to this client (unrevealed card for an AGENT)
)

/** Full board snapshot pushed to a client, already filtered for its role. */
@Serializable
data class StateDto(
    val type: String = "state",
    val version: Int,
    val status: String,
    val startingTeam: String,
    val currentTurn: String,
    val redRemaining: Int,
    val blueRemaining: Int,
    val role: String,
    val canReveal: Boolean,
    val cards: List<CardDto>
)

/** Action message sent from a client (only honored from the HOST). */
@Serializable
data class ActionDto(
    val action: String,
    val index: Int? = null
)

/**
 * Builds a role-filtered snapshot. AGENT clients never receive the color of unrevealed cards,
 * which prevents reading the secret key map via browser dev tools.
 */
fun GameEngine.snapshotFor(role: Role): StateDto {
    val showSecretColors = role == Role.HOST || role == Role.SPYMASTER
    val dtoCards = cards.map { card ->
        val visibleColor = when {
            card.revealed -> card.color.name
            showSecretColors -> card.color.name
            else -> null
        }
        CardDto(word = card.word, revealed = card.revealed, color = visibleColor)
    }
    return StateDto(
        version = version,
        status = status.name,
        startingTeam = startingTeam.name,
        currentTurn = currentTurn.name,
        redRemaining = remaining(Team.RED),
        blueRemaining = remaining(Team.BLUE),
        role = role.name,
        canReveal = role == Role.HOST,
        cards = dtoCards
    )
}
