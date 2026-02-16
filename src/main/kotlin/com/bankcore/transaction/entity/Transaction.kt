package com.bankcore.transaction.entity

import com.bankcore.account.entity.Account
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transaction")
data class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "transaction_number", length = 30, nullable = false, unique = true)
    val transactionNumber: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_number", referencedColumnName = "accountNumber", nullable = false)
    val account: Account,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: TransactionType,

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    val amount: BigDecimal,

    @Column(name = "balance_after", precision = 18, scale = 2, nullable = false)
    val balanceAfter: BigDecimal,

    @Column(name = "transaction_at", nullable = false, updatable = false)
    val transactionAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)