package com.bankcore.product.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class ProductTest {

    @Test
    fun `should create Product with required fields`() {
        val product = Product(
            code = "SAV001",
            name = "Basic Savings"
        )

        assertEquals("SAV001", product.code)
        assertEquals("Basic Savings", product.name)
        assertNotNull(product.createdAt)
        assertNotNull(product.updatedAt)
    }

    @Test
    fun `should create Product with all fields`() {
        val product = Product(
            code = "SAV001",
            name = "Basic Savings",
            description = "Basic savings account",
            interestRate = BigDecimal("0.0250")
        )

        assertEquals("SAV001", product.code)
        assertEquals("Basic Savings", product.name)
        assertEquals("Basic savings account", product.description)
        assertEquals(BigDecimal("0.0250"), product.interestRate)
    }

    @Test
    fun `should handle null optional fields`() {
        val product = Product(
            code = "SAV001",
            name = "Basic Savings",
            description = null,
            interestRate = null
        )

        assertEquals("SAV001", product.code)
        assertEquals("Basic Savings", product.name)
        assertEquals(null, product.description)
        assertEquals(null, product.interestRate)
    }
}