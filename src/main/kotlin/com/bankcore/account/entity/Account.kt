package com.bankcore.account.entity

import com.bankcore.product.entity.Product
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "account")
data class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "customer_id", nullable = false)
    val customerId: Long,

    @Column(name = "account_number", length = 20, nullable = false, unique = true)
    val accountNumber: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_code", referencedColumnName = "code", nullable = false)
    val product: Product,

    @Column(name = "balance", precision = 18, scale = 2, nullable = false)
    val balance: BigDecimal = BigDecimal("0.00"),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: AccountStatus = AccountStatus.ACTIVE,

    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0,

    @Column(name = "opened_at", updatable = false)
    val openedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "closed_at")
    val closedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
