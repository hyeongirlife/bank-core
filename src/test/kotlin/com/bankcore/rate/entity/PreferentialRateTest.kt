package com.bankcore.rate.entity

import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PreferentialRateTest {

    @Test
    fun `상품 기준 우대금리를 생성한다`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val preferentialRate = PreferentialRate(
            product = product,
            conditionCode = "SALARY_TRANSFER",
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0020")
        )

        assertEquals("SAV001", preferentialRate.product.code)
        assertEquals("SALARY_TRANSFER", preferentialRate.conditionCode)
        assertEquals(LocalDate.of(2026, 2, 20), preferentialRate.businessDate)
        assertEquals(BigDecimal("0.0020"), preferentialRate.rate)
        assertTrue(preferentialRate.isActive)
    }
}
