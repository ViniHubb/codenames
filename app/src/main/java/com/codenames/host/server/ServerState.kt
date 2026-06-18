package com.codenames.host.server

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide bridge between the [ServerService] (which owns the running [GameServer]) and the
 * Compose UI, which observes these flows and triggers host actions.
 */
object ServerState {
    val running = MutableStateFlow(false)
    val url = MutableStateFlow<String?>(null)
    val playerCount = MutableStateFlow(0)

    @Volatile
    var server: GameServer? = null
}
