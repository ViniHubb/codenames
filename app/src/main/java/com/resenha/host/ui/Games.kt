package com.resenha.host.ui

import androidx.compose.ui.graphics.Color
import com.resenha.host.server.ServerState
import com.resenha.host.server.codenames.CodenamesGame
import com.resenha.host.server.codenames.GameMode
import com.resenha.host.server.sintonia.SintoniaGame
import com.resenha.host.server.sintonia.SintoniaMode
import kotlinx.coroutines.flow.StateFlow

/**
 * The catalogue the native UI renders: blurb, themed modes and mode switching per game. Adding a
 * game is one entry here — give it an [accent] hue far from the ones already taken, since the
 * lobby tells the games apart by colour alone.
 */
enum class CatalogGame(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    /** Identity colour, lifted enough to stay legible on the dark background. */
    val accent: Color
) {
    CODENAMES(
        id = CodenamesGame.ID,
        title = "Codenames",
        tagline = "Dedução em times",
        description = "O Espião Mestre dá uma dica de uma palavra só e os agentes tentam " +
            "descobrir quais palavras são do time, sem esbarrar no assassino.",
        accent = Color(0xFFE0574A) // the board red, lifted for contrast on the dark surface
    ),
    SINTONIA(
        id = SintoniaGame.ID,
        title = "Sintonia",
        tagline = "Telepatia e debate",
        description = "O mestre vê um alvo escondido entre dois conceitos opostos e dá uma " +
            "dica. O time chuta a posição e o time adversário aposta de que lado o alvo está.",
        accent = Color(0xFF3FBDB0) // the teal from the dial's outer scoring band
    );

    /** Labels of this game's themed modes, in order, for the segmented picker. */
    val modeLabels: List<String>
        get() = when (this) {
            CODENAMES -> GameMode.entries.map { it.label }
            SINTONIA -> SintoniaMode.entries.map { it.label }
        }

    /** Selected mode. StateFlow is covariant, so the per-game enums collapse without a projection. */
    val selectedModeFlow: StateFlow<Enum<*>>
        get() = when (this) {
            CODENAMES -> ServerState.codenamesMode
            SINTONIA -> ServerState.sintoniaMode
        }

    /** Applies the mode at [index] to the running server. */
    fun selectMode(index: Int) {
        val server = ServerState.server ?: return
        when (this) {
            CODENAMES -> GameMode.entries.getOrNull(index)?.let { server.setCodenamesMode(it) }
            SINTONIA -> SintoniaMode.entries.getOrNull(index)?.let { server.setSintoniaMode(it) }
        }
    }

    companion object {
        fun from(id: String): CatalogGame = entries.firstOrNull { it.id == id } ?: CODENAMES
    }
}
