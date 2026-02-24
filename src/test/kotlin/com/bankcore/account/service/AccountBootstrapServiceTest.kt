package com.bankcore.account.service

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AccountBootstrapServiceTest {

    companion object {
        private const val CUSTOMER_ID = 100L
        private const val PRODUCT_A = "SAV001"
        private const val PRODUCT_B = "CHK001"
    }

    @Mock lateinit var accountRepository: AccountRepository
    @Mock lateinit var productRepository: ProductRepository
    @Mock lateinit var accountNumberGenerator: AccountNumberGenerator
    @Mock lateinit var distributedLockService: DistributedLockService

    private lateinit var accountBootstrapService: AccountBootstrapService

    private val productA = Product(id = 1L, code = PRODUCT_A, name = "Savings Product")
    private val productB = Product(id = 2L, code = PRODUCT_B, name = "Checking Product")

    @BeforeEach
    fun setUp() {
        accountBootstrapService = AccountBootstrapService(
            accountRepository = accountRepository,
            productRepository = productRepository,
            accountNumberGenerator = accountNumberGenerator,
            distributedLockService = distributedLockService,
            bootstrapProductCodes = "$PRODUCT_A,$PRODUCT_B"
        )
    }

    @Test
    fun `최초 로그인 시 초기 계좌를 생성한다`() {
        stubLockExecution()
        whenever(
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                CUSTOMER_ID,
                PRODUCT_A,
                AccountStatus.ACTIVE
            )
        ).thenReturn(null)
        whenever(
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                CUSTOMER_ID,
                PRODUCT_B,
                AccountStatus.ACTIVE
            )
        ).thenReturn(null)
        whenever(productRepository.findByCode(PRODUCT_A)).thenReturn(productA)
        whenever(productRepository.findByCode(PRODUCT_B)).thenReturn(productB)
        whenever(accountNumberGenerator.generate()).thenReturn("110-111-111111", "110-222-222222")
        whenever(accountRepository.save(any<Account>())).thenAnswer {
            val account = it.arguments[0] as Account
            if (account.product.code == PRODUCT_A) {
                account.copy(id = 10L)
            } else {
                account.copy(id = 20L)
            }
        }

        val result = accountBootstrapService.upsertInitialAccounts(CUSTOMER_ID)

        assertEquals(2, result.size)
        assertEquals(listOf(PRODUCT_A, PRODUCT_B), result.map { it.productCode })
        assertTrue(result.all { it.customerId == CUSTOMER_ID })
        verify(accountRepository, times(2)).save(any<Account>())
        verify(distributedLockService).executeWithLock(eq("account-bootstrap"), eq("$CUSTOMER_ID"), any<() -> Any>())
    }

    @Test
    fun `재로그인 시 이미 존재하는 계좌를 재사용한다`() {
        stubLockExecution()
        whenever(
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                CUSTOMER_ID,
                PRODUCT_A,
                AccountStatus.ACTIVE
            )
        ).thenReturn(activeAccount(id = 11L, product = productA))
        whenever(
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                CUSTOMER_ID,
                PRODUCT_B,
                AccountStatus.ACTIVE
            )
        ).thenReturn(activeAccount(id = 22L, product = productB))

        val result = accountBootstrapService.upsertInitialAccounts(CUSTOMER_ID)

        assertEquals(listOf(11L, 22L), result.map { it.id })
        verify(accountRepository, never()).save(any<Account>())
        verify(productRepository, never()).findByCode(any())
    }

    @Test
    fun `부분 생성 상태에서는 누락된 계좌만 생성한다`() {
        stubLockExecution()
        whenever(
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                CUSTOMER_ID,
                PRODUCT_A,
                AccountStatus.ACTIVE
            )
        ).thenReturn(activeAccount(id = 11L, product = productA))
        whenever(
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                CUSTOMER_ID,
                PRODUCT_B,
                AccountStatus.ACTIVE
            )
        ).thenReturn(null)
        whenever(productRepository.findByCode(PRODUCT_B)).thenReturn(productB)
        whenever(accountNumberGenerator.generate()).thenReturn("110-222-222222")
        whenever(accountRepository.save(any<Account>())).thenAnswer {
            val account = it.arguments[0] as Account
            account.copy(id = 22L)
        }

        val result = accountBootstrapService.upsertInitialAccounts(CUSTOMER_ID)

        assertEquals(2, result.size)
        assertEquals(listOf(11L, 22L), result.map { it.id })
        verify(accountRepository, times(1)).save(any<Account>())
        verify(productRepository, times(1)).findByCode(PRODUCT_B)
    }

    @Test
    fun `초기 상품이 없으면 예외를 던진다`() {
        stubLockExecution()
        whenever(
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                CUSTOMER_ID,
                PRODUCT_A,
                AccountStatus.ACTIVE
            )
        ).thenReturn(null)
        whenever(productRepository.findByCode(PRODUCT_A)).thenReturn(null)

        val ex = assertThrows<NoSuchElementException> {
            accountBootstrapService.upsertInitialAccounts(CUSTOMER_ID)
        }

        assertEquals("초기화 상품을 찾을 수 없습니다: $PRODUCT_A", ex.message)
        verify(accountRepository, never()).save(any<Account>())
    }

    @Test
    fun `고객 ID가 1 미만이면 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            accountBootstrapService.upsertInitialAccounts(0L)
        }

        assertEquals("고객 ID는 1 이상이어야 합니다", ex.message)
        verify(distributedLockService, never()).executeWithLock(any(), any(), any<() -> Any>())
    }

    private fun stubLockExecution() {
        whenever(distributedLockService.executeWithLock(eq("account-bootstrap"), eq("$CUSTOMER_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
    }

    private fun activeAccount(id: Long, product: Product): Account {
        return Account(
            id = id,
            customerId = CUSTOMER_ID,
            accountNumber = "110-123-456$id",
            product = product,
            balance = BigDecimal("0.00"),
            status = AccountStatus.ACTIVE,
            openedAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
}
