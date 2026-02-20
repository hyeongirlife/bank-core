package com.bankcore.rate.entity

import com.bankcore.product.entity.Product
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
    name = "preferential_rate",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_preferential_rate_product_condition_date",
            columnNames = ["product_code", "condition_code", "business_date"]
        )
    ]
)
data class PreferentialRate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_code", referencedColumnName = "code", nullable = false)
    val product: Product,

    @Column(name = "condition_code", length = 50, nullable = false)
    val conditionCode: String,

    @Column(name = "business_date", nullable = false)
    val businessDate: LocalDate,

    @Column(name = "rate", precision = 5, scale = 4, nullable = false)
    val rate: BigDecimal,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
