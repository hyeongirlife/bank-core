package com.bankcore.rate.repository

import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import com.bankcore.rate.entity.SpreadRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate

@ActiveProfiles("local")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpreadRateRepositoryTest {

    @Autowired
    lateinit var spreadRateRepository: SpreadRateRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    @Test
    fun `상품 코드와 영업일로 활성 가산금리를 조회한다`() {
        val product = productRepository.save(
            Product(code = "S${System.nanoTime().toString().takeLast(10)}", name = "Basic Savings")
        )
        val businessDate = LocalDate.of(2026, 2, 20)

        spreadRateRepository.save(
            SpreadRate(
                product = product,
                businessDate = businessDate,
                rate = BigDecimal("0.0015"),
                isActive = true
            )
        )

        val found = spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue(product.code, businessDate)

        assertEquals(BigDecimal("0.0015"), found?.rate)
    }

    @Test
    fun `비활성 가산금리는 조회되지 않는다`() {
        val product = productRepository.save(
            Product(code = "S${System.nanoTime().toString().takeLast(10)}", name = "Flex Savings")
        )
        val businessDate = LocalDate.of(2026, 2, 20)

        spreadRateRepository.save(
            SpreadRate(
                product = product,
                businessDate = businessDate,
                rate = BigDecimal("0.0020"),
                isActive = false
            )
        )

        val found = spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue(product.code, businessDate)

        assertNull(found)
    }
}
