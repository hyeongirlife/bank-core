package com.bankcore.transaction.repository

import com.bankcore.transaction.entity.Transaction
import com.bankcore.transaction.entity.TransactionType
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun existsByTransactionNumber(transactionNumber: String): Boolean
    fun findAllByAccountIdAndType(accountId: Long, type: TransactionType): List<Transaction>
}
