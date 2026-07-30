package com.resenha.host.server.sintonia

import android.content.res.AssetManager
import com.resenha.host.server.json

/** Themed PT-BR spectrum decks; yields the draw pool for a [SintoniaMode]. Mirrors `WordBank`. */
class SpectrumBank(
    private val classico: List<Spectrum>,
    private val picante: List<Spectrum>
) {
    fun poolFor(mode: SintoniaMode): List<Spectrum> = when (mode) {
        SintoniaMode.CLASSICO -> classico
        SintoniaMode.PICANTE -> picante
        SintoniaMode.TUDO -> classico + picante
    }

    companion object {
        fun load(assets: AssetManager): SpectrumBank = SpectrumBank(
            classico = read(assets, "spectrums_classico.json"),
            picante = read(assets, "spectrums_picante.json")
        )

        private fun read(assets: AssetManager, name: String): List<Spectrum> {
            val text = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return json.decodeFromString<List<Spectrum>>(text)
        }
    }
}
