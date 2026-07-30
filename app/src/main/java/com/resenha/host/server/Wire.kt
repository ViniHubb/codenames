package com.resenha.host.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Viewing role, shared by every game. Unknown values fall back to VIEWER, the least privileged,
 * so a role from one game can't grant visibility in another.
 */
enum class Role {
    HOST, SPYMASTER, AGENT, VIEWER;

    companion object {
        fun from(raw: String?): Role = when (raw?.uppercase()) {
            "HOST" -> HOST
            "SPYMASTER" -> SPYMASTER
            "AGENT" -> AGENT
            else -> VIEWER
        }
    }
}

/** Shared JSON config for both serialization directions. */
val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Client action, honored only from the HOST. One shape for every game; each reads its own fields. */
@Serializable
data class ActionDto(
    val action: String,
    val index: Int? = null,  // codenames: card to reveal
    val value: Int? = null,  // sintonia: pointer position, 0..100
    val text: String? = null, // sintonia: the clue
    val side: String? = null  // sintonia: "LEFT" | "RIGHT" bet
)
