package com.resenha.host.server

/** The two competing teams. Shared by every hosted game. */
enum class Team {
    RED, BLUE;

    fun other(): Team = if (this == RED) BLUE else RED
}

/** Outcome of a match. Shared by every hosted game. */
enum class GameStatus { PLAYING, RED_WINS, BLUE_WINS }
