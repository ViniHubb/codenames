package com.resenha.host.server

import com.resenha.host.server.codenames.CodenamesGame
import com.resenha.host.server.codenames.GameMode
import com.resenha.host.server.sintonia.SintoniaMode
import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide bridge between [ServerService], which owns the [GameServer], and the Compose UI. */
object ServerState {
    val running = MutableStateFlow(false)
    val url = MutableStateFlow<String?>(null)
    val playerCount = MutableStateFlow(0)
    val activeGame = MutableStateFlow(CodenamesGame.ID)
    val codenamesMode = MutableStateFlow(GameMode.CLASSICO)
    val sintoniaMode = MutableStateFlow(SintoniaMode.CLASSICO)

    @Volatile
    var server: GameServer? = null
}
