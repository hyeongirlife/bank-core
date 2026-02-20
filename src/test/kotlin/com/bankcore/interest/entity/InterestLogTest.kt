package com.bankcore.interest.entity

import com.bankcore.account.entity.Account
import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class InterestLogTest {

    @Test
    fun `계좌 일별 이자 로그를 생성한다`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val account = Account(customerId = 1L, accountNumber = "110-123-456789", product = product)

        val interestLog = InterestLog(
            account = account,
            businessDate = LocalDate.of(2026, 2, 20),
            baseRate = BigDecimal("0.0300"),
            spreadRate = BigDecimal("0.0010"),
            preferentialRate = BigDecimal("0.0020"),
            appliedRate = BigDecimal("0.0330"),
            balanceSnapshot = BigDecimal("1000000.00"),
            interestAmount = BigDecimal("90.00")
        )

        assertEquals(account, interestLog.account)
        assertEquals(LocalDate.of(2026, 2, 20), interestLog.businessDate)
        assertEquals(BigDecimal("0.0330"), interestLog.appliedRate)
        assertEquals(BigDecimal("1000000.00"), interestLog.balanceSnapshot)
        assertEquals(BigDecimal("90.00"), interestLog.interestAmount)
        assertNotNull(interestLog.createdAt)
    }
}
