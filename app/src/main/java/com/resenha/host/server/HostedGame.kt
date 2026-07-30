package com.resenha.host.server

/**
 * A game the [GameServer] can host; one is active at a time. Snapshots come back already
 * serialized, so each game defines its own state shape. Every snapshot must carry
 * `"type":"state"` and `"game":"<id>"` — clients follow the latter when the host switches games.
 */
interface HostedGame {
    /** Stable id, also the URL prefix of the game's web client (e.g. `/codenames/`). */
    val id: String

    /** State as seen by [role], serialized. Called under the server's lock. */
    fun snapshotJson(role: Role): String

    /** Applies a host action under the server's lock; unknown actions must be ignored. */
    fun handle(action: ActionDto)
}
