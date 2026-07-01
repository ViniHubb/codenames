package com.codenames.host

import com.codenames.host.server.GameMode
import com.codenames.host.server.WordBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WordBankTest {

    private fun bank() = WordBank(
        substantivos = (1..50).map { "S$it" },
        adjetivos = (1..50).map { "A$it" },
        verbos = (1..50).map { "V$it" }
    )

    @Test
    fun `tudo mix has 25 distinct words balanced across groups`() {
        val pool = bank().poolFor(GameMode.TUDO, Random(1))
        assertEquals(25, pool.size)
        assertEquals(25, pool.toSet().size) // all distinct
        assertEquals(9, pool.count { it.startsWith("S") })
        assertEquals(8, pool.count { it.startsWith("A") })
        assertEquals(8, pool.count { it.startsWith("V") })
    }

    @Test
    fun `single mode returns only its theme words`() {
        assertTrue(bank().poolFor(GameMode.ADJETIVOS).all { it.startsWith("A") })
        assertTrue(bank().poolFor(GameMode.VERBOS).all { it.startsWith("V") })
    }
}
