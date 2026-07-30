package com.resenha.host.server.sintonia

import android.content.res.AssetManager
import com.resenha.host.server.ActionDto
import com.resenha.host.server.HostedGame
import com.resenha.host.server.Role
import com.resenha.host.server.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Sintonia as a [HostedGame]: the engine, its spectrum bank and the selected theme. */
class SintoniaGame(assets: AssetManager) : HostedGame {

    override val id: String = ID

    private val bank = SpectrumBank.load(assets)
    private val engine = SintoniaEngine(bank.poolFor(SintoniaMode.CLASSICO))

    private val _mode = MutableStateFlow(SintoniaMode.CLASSICO)
    val mode: StateFlow<SintoniaMode> = _mode

    override fun snapshotJson(role: Role): String =
        json.encodeToString(SintoniaStateDto.serializer(), engine.snapshotFor(role))

    override fun handle(action: ActionDto) {
        when (action.action) {
            "setClue" -> engine.setClue(action.text.orEmpty())
            "setPointer" -> action.value?.let { engine.setPointer(it) }
            "lockGuess" -> engine.lockGuess()
            "setBet" -> Side.from(action.side)?.let { engine.setBet(it) }
            "nextRound" -> engine.nextRound()
            "newGame" -> engine.newGame(bank.poolFor(_mode.value))
        }
    }

    /** Switches the spectrum deck and starts a fresh match. Called under the server's lock. */
    fun setMode(mode: SintoniaMode) {
        _mode.value = mode
        engine.newGame(bank.poolFor(mode))
    }

    companion object {
        const val ID = "sintonia"
    }
}
