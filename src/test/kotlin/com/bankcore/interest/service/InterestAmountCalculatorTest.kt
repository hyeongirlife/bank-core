package com.bankcore.interest.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InterestAmountCalculatorTest {

    private val calculator = InterestAmountCalculator()

    @Test
    fun `일 이자를 365 분할하고 소수점 둘째 자리 절사로 계산한다`() {
        val balance = BigDecimal("1000000.00")
        val appliedRate = BigDecimal("0.0330")

        val result = calculator.calculateDailyInterest(balance, appliedRate)

        assertEquals(BigDecimal("90.41"), result)
    }

    @Test
    fun `적용금리는 기준 가산 우대 합으로 계산한다`() {
        val baseRate = BigDecimal("0.0300")
        val spreadRate = BigDecimal("0.0015")
        val preferentialRate = BigDecimal("0.0025")

        val result = calculator.calculateAppliedRate(baseRate, spreadRate, preferentialRate)

        assertEquals(BigDecimal("0.0340"), result)
    }
}
