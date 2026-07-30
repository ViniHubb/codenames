package com.resenha.host

import com.resenha.host.server.sintonia.SintoniaMode
import com.resenha.host.server.sintonia.SpectrumBank
import com.resenha.host.server.sintonia.Spectrum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumBankTest {

    private fun bank() = SpectrumBank(
        classico = (1..20).map { Spectrum("C$it", "c$it") },
        picante = (1..10).map { Spectrum("P$it", "p$it") }
    )

    @Test
    fun `single mode returns only its theme`() {
        assertTrue(bank().poolFor(SintoniaMode.CLASSICO).all { it.left.startsWith("C") })
        assertTrue(bank().poolFor(SintoniaMode.PICANTE).all { it.left.startsWith("P") })
    }

    @Test
    fun `tudo joins both decks`() {
        val pool = bank().poolFor(SintoniaMode.TUDO)
        assertEquals(30, pool.size)
        assertEquals(20, pool.count { it.left.startsWith("C") })
        assertEquals(10, pool.count { it.left.startsWith("P") })
    }
}
