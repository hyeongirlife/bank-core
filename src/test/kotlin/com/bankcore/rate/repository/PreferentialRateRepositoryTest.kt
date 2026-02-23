package com.bankcore.rate.repository

import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import com.bankcore.rate.entity.PreferentialRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PreferentialRateRepositoryTest {

    @Autowired
    lateinit var preferentialRateRepository: PreferentialRateRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    @Test
    fun `상품 코드와 영업일로 활성 우대금리 목록을 조회한다`() {
        val product = productRepository.save(
            Product(code = "S${System.nanoTime().toString().takeLast(10)}", name = "Basic Savings")
        )
        val businessDate = LocalDate.of(2026, 2, 20)

        preferentialRateRepository.save(
            PreferentialRate(
                product = product,
                conditionCode = "SALARY_TRANSFER",
                businessDate = businessDate,
                rate = BigDecimal("0.0020"),
                isActive = true
            )
        )
        preferentialRateRepository.save(
            PreferentialRate(
                product = product,
                conditionCode = "CARD_USAGE",
                businessDate = businessDate,
                rate = BigDecimal("0.0010"),
                isActive = true
            )
        )
        preferentialRateRepository.save(
            PreferentialRate(
                product = product,
                conditionCode = "INACTIVE_CASE",
                businessDate = businessDate,
                rate = BigDecimal("0.0030"),
                isActive = false
            )
        )

        val found = preferentialRateRepository.findAllByProductCodeAndBusinessDateAndIsActiveTrue(product.code, businessDate)

        assertEquals(2, found.size)
        assertEquals(setOf("SALARY_TRANSFER", "CARD_USAGE"), found.map { it.conditionCode }.toSet())
    }

    @Test
    fun `상품 코드 조건코드 목록 영업일로 활성 우대금리 목록을 조회한다`() {
        val product = productRepository.save(
            Product(code = "S${System.nanoTime().toString().takeLast(10)}", name = "Basic Savings")
        )
        val businessDate = LocalDate.of(2026, 2, 20)

        preferentialRateRepository.save(
            PreferentialRate(
                product = product,
                conditionCode = "SALARY_TRANSFER",
                businessDate = businessDate,
                rate = BigDecimal("0.0020"),
                isActive = true
            )
        )
        preferentialRateRepository.save(
            PreferentialRate(
                product = product,
                conditionCode = "CARD_USAGE",
                businessDate = businessDate,
                rate = BigDecimal("0.0010"),
                isActive = true
            )
        )
        preferentialRateRepository.save(
            PreferentialRate(
                product = product,
                conditionCode = "NEW_CUSTOMER",
                businessDate = businessDate,
                rate = BigDecimal("0.0015"),
                isActive = true
            )
        )

        val found = preferentialRateRepository.findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
            product.code,
            listOf("SALARY_TRANSFER", "CARD_USAGE", "NOT_EXISTS"),
            businessDate
        )

        assertEquals(2, found.size)
        assertEquals(setOf("SALARY_TRANSFER", "CARD_USAGE"), found.map { it.conditionCode }.toSet())
    }
}
