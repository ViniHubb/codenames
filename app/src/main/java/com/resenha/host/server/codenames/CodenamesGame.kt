package com.resenha.host.server.codenames

import android.content.res.AssetManager
import com.resenha.host.server.ActionDto
import com.resenha.host.server.HostedGame
import com.resenha.host.server.Role
import com.resenha.host.server.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Codenames as a [HostedGame]: the engine, its word bank and the selected theme. */
class CodenamesGame(assets: AssetManager) : HostedGame {

    override val id: String = ID

    private val bank = WordBank.load(assets)
    private val engine = GameEngine(bank.poolFor(GameMode.CLASSICO))

    private val _mode = MutableStateFlow(GameMode.CLASSICO)
    val mode: StateFlow<GameMode> = _mode

    override fun snapshotJson(role: Role): String =
        json.encodeToString(StateDto.serializer(), engine.snapshotFor(role))

    override fun handle(action: ActionDto) {
        when (action.action) {
            "reveal" -> action.index?.let { engine.reveal(it) }
            "passTurn" -> engine.passTurn()
            "newGame" -> engine.newGame(bank.poolFor(_mode.value))
        }
    }

    /** Switches the word theme and deals a fresh board. Called under the server's lock. */
    fun setMode(mode: GameMode) {
        _mode.value = mode
        engine.newGame(bank.poolFor(mode))
    }

    companion object {
        const val ID = "codenames"
    }
}
