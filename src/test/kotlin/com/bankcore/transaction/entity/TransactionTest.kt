package com.bankcore.transaction.entity

import com.bankcore.account.entity.Account
import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class TransactionTest {

    private val product = Product(code = "SAV001", name = "Basic Savings")
    private val account = Account(customerId = 1L, accountNumber = "110-123-456789", product = product)

    @Test
    fun `should create deposit Transaction`() {
        val tx = Transaction(
            transactionNumber = "TXN202401010001",
            account = account,
            type = TransactionType.DEPOSIT,
            amount = BigDecimal("1000.00"),
            balanceAfter = BigDecimal("1000.00")
        )

        assertEquals("TXN202401010001", tx.transactionNumber)
        assertEquals(account, tx.account)
        assertEquals(TransactionType.DEPOSIT, tx.type)
        assertEquals(BigDecimal("1000.00"), tx.amount)
        assertEquals(BigDecimal("1000.00"), tx.balanceAfter)
    }

    @Test
    fun `should create withdrawal Transaction`() {
        val tx = Transaction(
            transactionNumber = "TXN202401010002",
            account = account,
            type = TransactionType.WITHDRAWAL,
            amount = BigDecimal("500.00"),
            balanceAfter = BigDecimal("500.00")
        )

        assertEquals(TransactionType.WITHDRAWAL, tx.type)
    }

    @Test
    fun `should create transfer transactions`() {
        val txOut = Transaction(
            transactionNumber = "TXN202401010003",
            account = account,
            type = TransactionType.TRANSFER_OUT,
            amount = BigDecimal("200.00"),
            balanceAfter = BigDecimal("300.00")
        )
        val txIn = Transaction(
            transactionNumber = "TXN202401010004",
            account = account,
            type = TransactionType.TRANSFER_IN,
            amount = BigDecimal("200.00"),
            balanceAfter = BigDecimal("500.00")
        )

        assertEquals(TransactionType.TRANSFER_OUT, txOut.type)
        assertEquals(TransactionType.TRANSFER_IN, txIn.type)
    }
}
