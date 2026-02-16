package com.bankcore.account.entity

import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class AccountTest {

    @Test
    fun `should create Account with required fields`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val account = Account(
            accountNumber = "ACC123456789",
            product = product
        )

        assertEquals("ACC123456789", account.accountNumber)
        assertEquals(product, account.product)
        assertEquals(BigDecimal("0.00"), account.balance)
        assertEquals(AccountStatus.ACTIVE, account.status)
        assertNotNull(account.openedAt)
        assertNull(account.closedAt)
        assertNotNull(account.createdAt)
        assertNotNull(account.updatedAt)
    }

    @Test
    fun `should create Account with custom balance`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val account = Account(
            accountNumber = "ACC123456789",
            product = product,
            balance = BigDecimal("1000.50")
        )

        assertEquals(BigDecimal("1000.50"), account.balance)
    }

    @Test
    fun `should create closed Account`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val account = Account(
            accountNumber = "ACC123456789",
            product = product,
            status = AccountStatus.CLOSED
        )

        assertEquals(AccountStatus.CLOSED, account.status)
    }
}