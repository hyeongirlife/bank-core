package com.bankcore.account.service

import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

    @Mock lateinit var accountRepository: AccountRepository
    @Mock lateinit var productRepository: ProductRepository
    @Mock lateinit var accountNumberGenerator: AccountNumberGenerator

    @InjectMocks lateinit var accountService: AccountService

    private val product = Product(id = 1L, code = "SAV001", name = "Basic Savings")

    @Test
    fun `should create account successfully`() {
        val request = AccountCreateRequest(customerId = 1L, productCode = "SAV001")

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(accountNumberGenerator.generate()).thenReturn("110-123-456789")
        whenever(accountRepository.save(any<Account>())).thenAnswer {
            val account = it.arguments[0] as Account
            account.copy(id = 1L)
        }

        val response = accountService.createAccount(request)

        assertEquals(1L, response.customerId)
        assertEquals("110-123-456789", response.accountNumber)
        assertEquals("SAV001", response.productCode)
        assertEquals(BigDecimal("0.00"), response.balance)
        assertEquals(AccountStatus.ACTIVE, response.status)
    }

    @Test
    fun `should throw exception when product not found`() {
        val request = AccountCreateRequest(customerId = 1L, productCode = "INVALID")

        whenever(productRepository.findByCode("INVALID")).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            accountService.createAccount(request)
        }
        assertEquals("상품을 찾을 수 없습니다: INVALID", ex.message)
    }

    @Test
    fun `should throw exception when max account limit exceeded`() {
        val limitedProduct = Product(id = 1L, code = "SAV001", name = "Basic Savings", maxAccountPerCustomer = 1)
        val request = AccountCreateRequest(customerId = 1L, productCode = "SAV001")

        whenever(productRepository.findByCode("SAV001")).thenReturn(limitedProduct)
        whenever(accountRepository.countByCustomerIdAndProductCodeAndStatus(1L, "SAV001", AccountStatus.ACTIVE)).thenReturn(1)

        val ex = assertThrows<IllegalStateException> {
            accountService.createAccount(request)
        }
        assertEquals("해당 상품의 최대 계좌 개설 수를 초과했습니다", ex.message)
    }
}
