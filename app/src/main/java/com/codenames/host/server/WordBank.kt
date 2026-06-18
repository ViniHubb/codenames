package com.codenames.host.server

import android.content.res.AssetManager

/** Loads the bundled PT-BR word list from assets/words_ptbr.json. */
object WordBank {
    fun load(assets: AssetManager): List<String> {
        val text = assets.open("words_ptbr.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return json.decodeFromString<List<String>>(text)
    }
}
