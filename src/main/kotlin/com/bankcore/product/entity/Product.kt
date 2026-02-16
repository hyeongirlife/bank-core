package com.bankcore.product.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "product")
data class Product(
    @Id
    @Column(name = "code", length = 20)
    val code: String,

    @Column(name = "name", length = 100, nullable = false)
    val name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "interest_rate", precision = 5, scale = 4)
    val interestRate: BigDecimal? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)