package com.bankcore.interest.service

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class InterestAmountCalculator {
    companion object {
        private val DAY_COUNT = BigDecimal("365")
        private const val RATE_SCALE = 4
        private const val MONEY_SCALE = 2
    }

    fun calculateAppliedRate(baseRate: BigDecimal, spreadRate: BigDecimal, preferentialRate: BigDecimal): BigDecimal {
        return baseRate
            .add(spreadRate)
            .add(preferentialRate)
            .setScale(RATE_SCALE, RoundingMode.DOWN)
    }

    fun calculateDailyInterest(balance: BigDecimal, appliedRate: BigDecimal): BigDecimal {
        return balance
            .multiply(appliedRate)
            .divide(DAY_COUNT, MONEY_SCALE, RoundingMode.DOWN)
    }
}
