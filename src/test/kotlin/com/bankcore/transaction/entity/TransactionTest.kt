package com.bankcore.transaction.entity

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class TransactionTest {

    @Test
    fun `should create Transaction with required fields`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val account = Account(accountNumber = "ACC123456789", product = product)
        
        val transaction = Transaction(
            transactionNumber = "TXN202401010001",
            account = account,
            type = TransactionType.DEPOSIT,
            amount = BigDecimal("1000.00"),
            balanceAfter = BigDecimal("1000.00")
        )

        assertEquals("TXN202401010001", transaction.transactionNumber)
        assertEquals(account, transaction.account)
        assertEquals(TransactionType.DEPOSIT, transaction.type)
        assertEquals(BigDecimal("1000.00"), transaction.amount)
        assertEquals(BigDecimal("1000.00"), transaction.balanceAfter)
        assertNotNull(transaction.transactionAt)
        assertNotNull(transaction.createdAt)
        assertNotNull(transaction.updatedAt)
    }

    @Test
    fun `should create withdrawal Transaction`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val account = Account(accountNumber = "ACC123456789", product = product)
        
        val transaction = Transaction(
            transactionNumber = "TXN202401010002",
            account = account,
            type = TransactionType.WITHDRAWAL,
            amount = BigDecimal("500.00"),
            balanceAfter = BigDecimal("500.00")
        )

        assertEquals(TransactionType.WITHDRAWAL, transaction.type)
        assertEquals(BigDecimal("500.00"), transaction.amount)
    }

    @Test
    fun `should create transfer transactions`() {
        val product = Product(code = "SAV001", name = "Basic Savings")
        val account = Account(accountNumber = "ACC123456789", product = product)
        
        val transferOut = Transaction(
            transactionNumber = "TXN202401010003",
            account = account,
            type = TransactionType.TRANSFER_OUT,
            amount = BigDecimal("200.00"),
            balanceAfter = BigDecimal("300.00")
        )

        val transferIn = Transaction(
            transactionNumber = "TXN202401010004",
            account = account,
            type = TransactionType.TRANSFER_IN,
            amount = BigDecimal("200.00"),
            balanceAfter = BigDecimal("500.00")
        )

        assertEquals(TransactionType.TRANSFER_OUT, transferOut.type)
        assertEquals(TransactionType.TRANSFER_IN, transferIn.type)
    }
}