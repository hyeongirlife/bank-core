package com.bankcore.interest.entity

import com.bankcore.account.entity.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    name = "interest_settlement",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_interest_settlement_account_type", columnNames = ["account_id", "settlement_type"])
    ]
)
data class InterestSettlement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "id", nullable = false)
    val account: Account,

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false)
    val settlementType: InterestSettlementType,

    @Column(name = "business_date", nullable = false)
    val businessDate: LocalDate,

    @Column(name = "interest_amount", precision = 18, scale = 2, nullable = false)
    val interestAmount: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
