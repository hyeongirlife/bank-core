package com.bankcore.account.e2e

import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import com.bankcore.testsupport.TestcontainersIntegrationBase
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

class AccountBootstrapE2ETest : TestcontainersIntegrationBase() {

    companion object {
        private const val CUSTOMER_ID = 910001L
        private const val CUSTOMER_ID_PARTIAL = 910002L
        private const val PRODUCT_A = "SAV001"
        private const val PRODUCT_B = "CHK001"
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var accountRepository: AccountRepository

    @BeforeEach
    fun setUp() {
        ensureProduct(PRODUCT_A, "Basic Savings")
        ensureProduct(PRODUCT_B, "Basic Checking")
    }

    @Test
    fun `최초 로그인 bootstrap 호출 시 초기 계좌를 생성한다`() {
        val response = bootstrap(CUSTOMER_ID)

        assertEquals(2, response.size)
        assertEquals(listOf(PRODUCT_A, PRODUCT_B), response.map { it.productCode })
        assertTrue(response.all { it.customerId == CUSTOMER_ID })
        assertTrue(response.all { it.status == AccountStatus.ACTIVE })

        val activeCount = listOf(PRODUCT_A, PRODUCT_B).sumOf { code ->
            accountRepository.countByCustomerIdAndProductCodeAndStatus(CUSTOMER_ID, code, AccountStatus.ACTIVE).toInt()
        }
        assertEquals(2, activeCount)
    }

    @Test
    fun `재로그인 bootstrap 재호출 시 중복 생성하지 않는다`() {
        val first = bootstrap(CUSTOMER_ID)
        val second = bootstrap(CUSTOMER_ID)

        assertEquals(first.map { it.id }, second.map { it.id })

        val activeCount = listOf(PRODUCT_A, PRODUCT_B).sumOf { code ->
            accountRepository.countByCustomerIdAndProductCodeAndStatus(CUSTOMER_ID, code, AccountStatus.ACTIVE).toInt()
        }

        assertEquals(2, activeCount)
    }

    @Test
    fun `부분 생성 상태에서 bootstrap 호출 시 누락된 계좌만 생성한다`() {
        bootstrap(CUSTOMER_ID_PARTIAL)

        val existingA = accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
            CUSTOMER_ID_PARTIAL,
            PRODUCT_A,
            AccountStatus.ACTIVE
        ) ?: throw IllegalStateException("existing A account not found")

        val closedA = existingA.copy(
            status = AccountStatus.CLOSED,
            closedAt = existingA.updatedAt,
            updatedAt = existingA.updatedAt
        )
        accountRepository.save(closedA)

        val afterPartial = bootstrap(CUSTOMER_ID_PARTIAL)

        val activeCountA = accountRepository.countByCustomerIdAndProductCodeAndStatus(
            CUSTOMER_ID_PARTIAL,
            PRODUCT_A,
            AccountStatus.ACTIVE
        )
        val activeCountB = accountRepository.countByCustomerIdAndProductCodeAndStatus(
            CUSTOMER_ID_PARTIAL,
            PRODUCT_B,
            AccountStatus.ACTIVE
        )

        assertEquals(1, activeCountA)
        assertEquals(1, activeCountB)
        assertEquals(2, afterPartial.size)
        assertEquals(listOf(PRODUCT_A, PRODUCT_B), afterPartial.map { it.productCode })
    }

    private fun bootstrap(customerId: Long): List<AccountResponse> {
        val result = mockMvc.post("/api/accounts/bootstrap") {
            param("customerId", customerId.toString())
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val arrayType = objectMapper.typeFactory
            .constructCollectionType(List::class.java, AccountResponse::class.java)
        return objectMapper.readValue(result.response.contentAsString, arrayType)
    }

    private fun ensureProduct(code: String, name: String) {
        if (productRepository.findByCode(code) == null) {
            productRepository.save(Product(code = code, name = name))
        }
    }
}
