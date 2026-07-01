package com.codenames.host.server

/**
 * Game modes. They differ only in which word group fills the board:
 * - [CLASSICO]  substantivos (the original list)
 * - [ADJETIVOS] adjectives
 * - [VERBOS]    verbs
 * - [TUDO]      a balanced mix of all three
 */
enum class GameMode(val label: String) {
    CLASSICO("Clássico"),
    ADJETIVOS("Adjetivos"),
    VERBOS("Verbos"),
    TUDO("Tudo");

    companion object {
        fun from(raw: String?): GameMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CLASSICO
    }
}
