package com.bankcore.account.entity

import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class AccountTest {

    private val product = Product(code = "SAV001", name = "Basic Savings")

    @Test
    fun `should create Account with required fields`() {
        val account = Account(
            customerId = 1L,
            accountNumber = "110-123-456789",
            product = product
        )

        assertEquals(1L, account.customerId)
        assertEquals("110-123-456789", account.accountNumber)
        assertEquals(product, account.product)
        assertEquals(BigDecimal("0.00"), account.balance)
        assertEquals(AccountStatus.ACTIVE, account.status)
        assertNull(account.closedAt)
    }

    @Test
    fun `should create Account with custom balance`() {
        val account = Account(
            customerId = 1L,
            accountNumber = "110-123-456789",
            product = product,
            balance = BigDecimal("1000.50")
        )

        assertEquals(BigDecimal("1000.50"), account.balance)
    }

    @Test
    fun `should create closed Account`() {
        val account = Account(
            customerId = 1L,
            accountNumber = "110-123-456789",
            product = product,
            status = AccountStatus.CLOSED
        )

        assertEquals(AccountStatus.CLOSED, account.status)
    }
}
