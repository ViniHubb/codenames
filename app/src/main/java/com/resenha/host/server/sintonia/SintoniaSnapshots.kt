package com.resenha.host.server.sintonia

import com.resenha.host.server.Role
import kotlinx.serialization.Serializable

/** Full round snapshot pushed to a client, already filtered for its role. */
@Serializable
data class SintoniaStateDto(
    val type: String = "state",
    val game: String = SintoniaGame.ID,
    val version: Int,
    val status: String,
    val phase: String,
    val round: Int,
    val left: String,
    val right: String,
    val clue: String,
    val pointer: Int,
    val target: Int?, // null while the target must stay secret
    val bet: String?,
    val guessingTeam: String,
    val bettingTeam: String,
    val scoreRed: Int,
    val scoreBlue: Int,
    val lastGuessPoints: Int,
    val lastBetPoints: Int,
    val winningScore: Int,
    /** Half-widths of the 4/3/2 point bands, so the client can draw the target to scale. */
    val bands: List<Int>,
    val role: String,
    val canControl: Boolean
)

/**
 * Role-filtered snapshot. Only the HOST gets the target before the reveal; everyone else
 * receives null, so it can't be read out of the browser dev tools.
 */
fun SintoniaEngine.snapshotFor(role: Role): SintoniaStateDto {
    val revealed = phase == SintoniaPhase.REVEAL
    return SintoniaStateDto(
        version = version,
        status = status.name,
        phase = phase.name,
        round = round,
        left = spectrum.left,
        right = spectrum.right,
        clue = clue,
        pointer = pointer,
        target = if (role == Role.HOST || revealed) target else null,
        bet = bet?.name,
        guessingTeam = guessingTeam.name,
        bettingTeam = guessingTeam.other().name,
        scoreRed = scoreRed,
        scoreBlue = scoreBlue,
        lastGuessPoints = lastGuessPoints,
        lastBetPoints = lastBetPoints,
        winningScore = SintoniaEngine.WINNING_SCORE,
        bands = listOf(
            SintoniaEngine.BULLSEYE_HALF_WIDTH,
            SintoniaEngine.NEAR_HALF_WIDTH,
            SintoniaEngine.OUTER_HALF_WIDTH
        ),
        role = role.name,
        canControl = role == Role.HOST
    )
}
