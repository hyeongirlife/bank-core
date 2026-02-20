package com.bankcore.rate.entity

import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SpreadRateTest {

    @Test
    fun `상품 기준 가산금리를 생성한다`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val spreadRate = SpreadRate(
            product = product,
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0015")
        )

        assertEquals("SAV001", spreadRate.product.code)
        assertEquals(LocalDate.of(2026, 2, 20), spreadRate.businessDate)
        assertEquals(BigDecimal("0.0015"), spreadRate.rate)
        assertTrue(spreadRate.isActive)
    }
}
