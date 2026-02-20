package com.bankcore.interest.entity

import com.bankcore.account.entity.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "interest_log",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_interest_log_account_business_date", columnNames = ["account_id", "business_date"])
    ]
)
data class InterestLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "id", nullable = false)
    val account: Account,

    @Column(name = "business_date", nullable = false)
    val businessDate: LocalDate,

    @Column(name = "base_rate", precision = 5, scale = 4, nullable = false)
    val baseRate: BigDecimal,

    @Column(name = "spread_rate", precision = 5, scale = 4, nullable = false)
    val spreadRate: BigDecimal,

    @Column(name = "preferential_rate", precision = 5, scale = 4, nullable = false)
    val preferentialRate: BigDecimal,

    @Column(name = "applied_rate", precision = 5, scale = 4, nullable = false)
    val appliedRate: BigDecimal,

    @Column(name = "balance_snapshot", precision = 18, scale = 2, nullable = false)
    val balanceSnapshot: BigDecimal,

    @Column(name = "interest_amount", precision = 18, scale = 2, nullable = false)
    val interestAmount: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
