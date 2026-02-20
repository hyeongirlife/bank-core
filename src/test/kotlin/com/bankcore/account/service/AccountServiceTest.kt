package com.bankcore.account.service

import com.bankcore.account.dto.AccountBalanceChangeRequest
import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import com.bankcore.transaction.entity.Transaction
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.bankcore.transaction.service.TransactionNumberGenerator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val CUSTOMER_ID = 1L
        private const val PRODUCT_CODE = "SAV001"
        private const val ACCOUNT_NUMBER = "110-123-456789"
    }

    @Mock lateinit var accountRepository: AccountRepository
    @Mock lateinit var productRepository: ProductRepository
    @Mock lateinit var accountNumberGenerator: AccountNumberGenerator
    @Mock lateinit var distributedLockService: DistributedLockService
    @Mock lateinit var transactionRepository: TransactionRepository
    @Mock lateinit var transactionNumberGenerator: TransactionNumberGenerator

    @InjectMocks lateinit var accountService: AccountService

    private val product = Product(id = 1L, code = PRODUCT_CODE, name = "Basic Savings")

    @Test
    fun `계좌를 정상적으로 개설한다`() {
        val request = AccountCreateRequest(customerId = CUSTOMER_ID, productCode = PRODUCT_CODE)

        whenever(distributedLockService.executeWithLock(eq("account"), eq("$CUSTOMER_ID:$PRODUCT_CODE"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(productRepository.findByCode(PRODUCT_CODE)).thenReturn(product)
        whenever(accountNumberGenerator.generate()).thenReturn(ACCOUNT_NUMBER)
        whenever(accountRepository.save(any<Account>())).thenAnswer {
            val account = it.arguments[0] as Account
            account.copy(id = ACCOUNT_ID)
        }

        val response = accountService.createAccount(request)

        assertEquals(CUSTOMER_ID, response.customerId)
        assertEquals(ACCOUNT_NUMBER, response.accountNumber)
        assertEquals(PRODUCT_CODE, response.productCode)
        assertEquals(BigDecimal("0.00"), response.balance)
        assertEquals(AccountStatus.ACTIVE, response.status)
        verify(distributedLockService).executeWithLock(eq("account"), eq("$CUSTOMER_ID:$PRODUCT_CODE"), any<() -> Any>())
    }

    @Test
    fun `존재하지 않는 상품 코드로 개설 시 예외를 던진다`() {
        val request = AccountCreateRequest(customerId = 1L, productCode = "INVALID")

        whenever(distributedLockService.executeWithLock(eq("account"), eq("1:INVALID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(productRepository.findByCode("INVALID")).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            accountService.createAccount(request)
        }
        assertEquals("상품을 찾을 수 없습니다: INVALID", ex.message)
    }

    @Test
    fun `최대 계좌 수 초과 시 예외를 던진다`() {
        val limitedProduct = Product(id = 1L, code = "SAV001", name = "Basic Savings", maxAccountPerCustomer = 1)
        val request = AccountCreateRequest(customerId = 1L, productCode = "SAV001")

        whenever(distributedLockService.executeWithLock(eq("account"), eq("$CUSTOMER_ID:$PRODUCT_CODE"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(productRepository.findByCode("SAV001")).thenReturn(limitedProduct)
        whenever(accountRepository.countByCustomerIdAndProductCodeAndStatus(1L, "SAV001", AccountStatus.ACTIVE)).thenReturn(1)

        val ex = assertThrows<IllegalStateException> {
            accountService.createAccount(request)
        }
        assertEquals("해당 상품의 최대 계좌 개설 수를 초과했습니다", ex.message)
    }

    @Test
    fun `분산 락 획득 실패 시 예외를 던진다`() {
        val request = AccountCreateRequest(customerId = 1L, productCode = "SAV001")

        whenever(distributedLockService.executeWithLock(eq("account"), eq("$CUSTOMER_ID:$PRODUCT_CODE"), any<() -> Any>()))
            .thenThrow(IllegalStateException("현재 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요."))

        val ex = assertThrows<IllegalStateException> {
            accountService.createAccount(request)
        }
        assertEquals("현재 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.", ex.message)
    }

    @Test
    fun `ID로 계좌를 조회한다`() {
        val account = Account(
            id = 1L,
            customerId = 1L,
            accountNumber = "110-123-456789",
            product = product
        )
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(account))

        val response = accountService.getAccount(1L)

        assertEquals(1L, response.id)
        assertEquals("110-123-456789", response.accountNumber)
        assertEquals("SAV001", response.productCode)
    }

    @Test
    fun `존재하지 않는 ID로 조회 시 예외를 던진다`() {
        whenever(accountRepository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<NoSuchElementException> {
            accountService.getAccount(999L)
        }

        assertEquals("계좌를 찾을 수 없습니다: 999", ex.message)
    }

    @Test
    fun `계좌를 정상적으로 해지한다`() {
        val account = activeAccount()
        val lockKey = "$ACCOUNT_ID"

        whenever(distributedLockService.executeWithLock(eq("account"), eq(lockKey), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] as Account }

        val response = accountService.closeAccount(ACCOUNT_ID)

        assertEquals(AccountStatus.CLOSED, response.status)
        assertNotNull(response.closedAt)
        verify(distributedLockService).executeWithLock(eq("account"), eq(lockKey), any<() -> Any>())
    }

    @Test
    fun `존재하지 않는 계좌 해지 시 예외를 던진다`() {
        whenever(distributedLockService.executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty())

        val ex = assertThrows<NoSuchElementException> {
            accountService.closeAccount(ACCOUNT_ID)
        }

        assertEquals("계좌를 찾을 수 없습니다: $ACCOUNT_ID", ex.message)
    }

    @Test
    fun `이미 해지된 계좌 해지 시 예외를 던진다`() {
        whenever(distributedLockService.executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(closedAccount()))

        val ex = assertThrows<IllegalStateException> {
            accountService.closeAccount(ACCOUNT_ID)
        }

        assertEquals("이미 해지된 계좌입니다", ex.message)
    }

    @Test
    fun `잔액이 남아있는 계좌 해지 시 예외를 던진다`() {
        whenever(distributedLockService.executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount(balance = BigDecimal("100.00"))))

        val ex = assertThrows<IllegalStateException> {
            accountService.closeAccount(ACCOUNT_ID)
        }

        assertEquals("잔액이 남아있는 계좌는 해지할 수 없습니다", ex.message)
    }

    @Test
    fun `계좌 해지 시 account id 락을 사용한다`() {
        whenever(distributedLockService.executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount()))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] as Account }

        accountService.closeAccount(ACCOUNT_ID)

        verify(distributedLockService).executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>())
    }

    @Test
    fun `계좌 입금을 정상 처리한다`() {
        val request = AccountBalanceChangeRequest(amount = BigDecimal("1000000000.00"))
        whenever(distributedLockService.executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount()))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] as Account }
        whenever(transactionNumberGenerator.generate()).thenReturn("TXN-DEP-1")
        whenever(transactionRepository.save(any<Transaction>())).thenAnswer { it.arguments[0] as Transaction }

        val response = accountService.deposit(ACCOUNT_ID, request)

        assertEquals(BigDecimal("1000000000.00"), response.balance)
        verify(transactionRepository).save(check {
            assertEquals(TransactionType.DEPOSIT, it.type)
            assertEquals(BigDecimal("1000000000.00"), it.amount)
            assertEquals(BigDecimal("1000000000.00"), it.balanceAfter)
        })
    }

    @Test
    fun `계좌 출금을 정상 처리한다`() {
        val request = AccountBalanceChangeRequest(amount = BigDecimal("495000000.00"))
        whenever(distributedLockService.executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount(balance = BigDecimal("1000000000.00"))))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] as Account }
        whenever(transactionNumberGenerator.generate()).thenReturn("TXN-WIT-1")
        whenever(transactionRepository.save(any<Transaction>())).thenAnswer { it.arguments[0] as Transaction }

        val response = accountService.withdraw(ACCOUNT_ID, request)

        assertEquals(BigDecimal("505000000.00"), response.balance)
        verify(transactionRepository).save(check {
            assertEquals(TransactionType.WITHDRAWAL, it.type)
            assertEquals(BigDecimal("495000000.00"), it.amount)
            assertEquals(BigDecimal("505000000.00"), it.balanceAfter)
        })
    }

    @Test
    fun `잔액 초과 출금 시 예외를 던진다`() {
        val request = AccountBalanceChangeRequest(amount = BigDecimal("100.00"))
        whenever(distributedLockService.executeWithLock(eq("account"), eq("$ACCOUNT_ID"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount(balance = BigDecimal("10.00"))))

        val ex = assertThrows<IllegalStateException> {
            accountService.withdraw(ACCOUNT_ID, request)
        }

        assertEquals("잔액이 부족합니다", ex.message)
    }

    private fun activeAccount(balance: BigDecimal = BigDecimal("0.00")) = Account(
        id = ACCOUNT_ID,
        customerId = CUSTOMER_ID,
        accountNumber = ACCOUNT_NUMBER,
        product = product,
        balance = balance,
        status = AccountStatus.ACTIVE,
        openedAt = LocalDateTime.now(),
        closedAt = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private fun closedAccount() = Account(
        id = ACCOUNT_ID,
        customerId = CUSTOMER_ID,
        accountNumber = ACCOUNT_NUMBER,
        product = product,
        balance = BigDecimal("0.00"),
        status = AccountStatus.CLOSED,
        openedAt = LocalDateTime.now().minusDays(1),
        closedAt = LocalDateTime.now(),
        createdAt = LocalDateTime.now().minusDays(1),
        updatedAt = LocalDateTime.now()
    )
}
