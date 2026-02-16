package com.bankcore.product.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class ProductTest {

    @Test
    fun `should create Product with required fields`() {
        val product = Product(code = "SAV001", name = "Basic Savings")

        assertEquals("SAV001", product.code)
        assertEquals("Basic Savings", product.name)
        assertEquals(0, product.maxAccountPerCustomer)
        assertNull(product.id)
    }

    @Test
    fun `should create Product with all fields`() {
        val product = Product(
            code = "SAV001",
            name = "Basic Savings",
            description = "Basic savings account",
            interestRate = BigDecimal("0.0250"),
            maxAccountPerCustomer = 1
        )

        assertEquals("Basic savings account", product.description)
        assertEquals(BigDecimal("0.0250"), product.interestRate)
        assertEquals(1, product.maxAccountPerCustomer)
    }

    @Test
    fun `should default maxAccountPerCustomer to 0 meaning unlimited`() {
        val product = Product(code = "SAV001", name = "Basic Savings")

        assertEquals(0, product.maxAccountPerCustomer)
    }
}
