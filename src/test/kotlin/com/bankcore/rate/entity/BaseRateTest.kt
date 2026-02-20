package com.bankcore.rate.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class BaseRateTest {

    @Test
    fun `필수 필드로 기준금리를 생성한다`() {
        val baseRate = BaseRate(
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0325")
        )

        assertNull(baseRate.id)
        assertEquals(LocalDate.of(2026, 2, 20), baseRate.businessDate)
        assertEquals(BigDecimal("0.0325"), baseRate.rate)
        assertNotNull(baseRate.createdAt)
        assertNotNull(baseRate.updatedAt)
    }
}
