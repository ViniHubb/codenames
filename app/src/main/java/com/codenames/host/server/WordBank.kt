package com.codenames.host.server

import android.content.res.AssetManager
import kotlin.random.Random

/**
 * Holds the themed PT-BR word lists and produces the pool of words used to deal a board for a
 * given [GameMode].
 */
class WordBank(
    private val substantivos: List<String>,
    private val adjetivos: List<String>,
    private val verbos: List<String>
) {
    /**
     * Words to deal a board from. Single-theme modes return the whole theme list (the
     * [GameEngine] samples [GameEngine.BOARD_SIZE]). [GameMode.TUDO] returns exactly
     * [GameEngine.BOARD_SIZE] words, balanced across the three groups.
     */
    fun poolFor(mode: GameMode, rng: Random = Random.Default): List<String> = when (mode) {
        GameMode.CLASSICO -> substantivos
        GameMode.ADJETIVOS -> adjetivos
        GameMode.VERBOS -> verbos
        GameMode.TUDO -> balancedMix(rng)
    }

    /** ~9 substantivos + 8 adjetivos + 8 verbos, all distinct, totaling BOARD_SIZE (25). */
    private fun balancedMix(rng: Random): List<String> {
        val targets = listOf(substantivos to 9, adjetivos to 8, verbos to 8)
        val picked = LinkedHashSet<String>()
        for ((list, target) in targets) {
            var taken = 0
            for (word in list.shuffled(rng)) {
                if (taken >= target) break
                if (picked.add(word)) taken++
            }
        }
        // Safety net if a word appears in more than one group and reduced the count.
        if (picked.size < GameEngine.BOARD_SIZE) {
            for (word in (substantivos + adjetivos + verbos).shuffled(rng)) {
                if (picked.size >= GameEngine.BOARD_SIZE) break
                picked.add(word)
            }
        }
        return picked.toList()
    }

    companion object {
        fun load(assets: AssetManager): WordBank = WordBank(
            substantivos = read(assets, "words_substantivos.json"),
            adjetivos = read(assets, "words_adjetivos.json"),
            verbos = read(assets, "words_verbos.json")
        )

        private fun read(assets: AssetManager, name: String): List<String> {
            val text = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return json.decodeFromString<List<String>>(text)
        }
    }
}
