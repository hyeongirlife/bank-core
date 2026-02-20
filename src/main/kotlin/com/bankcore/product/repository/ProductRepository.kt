package com.bankcore.product.repository

import com.bankcore.product.entity.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {
    fun findByCode(code: String): Product?
}
