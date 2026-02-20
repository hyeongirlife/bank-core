package com.bankcore.transfer.service

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.product.entity.Product
import com.bankcore.transaction.entity.Transaction
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.bankcore.transaction.service.TransactionNumberGenerator
import com.bankcore.transfer.dto.TransferRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
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
class TransferServiceTest {

    companion object {
        private const val FROM_ACCOUNT_ID = 1L
        private const val TO_ACCOUNT_ID = 2L
        private val TRANSFER_AMOUNT = BigDecimal("300.00")
    }

    @Mock lateinit var accountRepository: AccountRepository
    @Mock lateinit var transactionRepository: TransactionRepository
    @Mock lateinit var transactionNumberGenerator: TransactionNumberGenerator
    @Mock lateinit var distributedLockService: DistributedLockService

    @InjectMocks lateinit var transferService: TransferService

    private val product = Product(id = 1L, code = "SAV001", name = "Basic Savings")

    @Test
    fun `계좌 간 송금을 정상 처리한다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = TO_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("1:2")

        whenever(accountRepository.findById(FROM_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(FROM_ACCOUNT_ID, BigDecimal("1000.00"))))
        whenever(accountRepository.findById(TO_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(TO_ACCOUNT_ID, BigDecimal("200.00"))))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] as Account }
        whenever(transactionNumberGenerator.generate()).thenReturn("TXN-OUT-1", "TXN-IN-1")
        whenever(transactionRepository.save(any<Transaction>())).thenAnswer { it.arguments[0] as Transaction }

        val response = transferService.transfer(request)

        assertEquals(FROM_ACCOUNT_ID, response.fromAccountId)
        assertEquals(TO_ACCOUNT_ID, response.toAccountId)
        assertEquals(TRANSFER_AMOUNT, response.amount)
        assertEquals(BigDecimal("700.00"), response.fromBalance)
        assertEquals(BigDecimal("500.00"), response.toBalance)
        assertNotNull(response.transferredAt)
    }

    @Test
    fun `동일 계좌 송금 시 예외를 던진다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = FROM_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )

        val ex = assertThrows<IllegalArgumentException> {
            transferService.transfer(request)
        }

        assertEquals("동일 계좌로는 송금할 수 없습니다", ex.message)
        verifyNoInteractions(distributedLockService)
    }

    @Test
    fun `출금 계좌가 없으면 예외를 던진다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = TO_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("1:2")

        whenever(accountRepository.findById(FROM_ACCOUNT_ID)).thenReturn(Optional.empty())

        val ex = assertThrows<NoSuchElementException> {
            transferService.transfer(request)
        }

        assertEquals("출금 계좌를 찾을 수 없습니다: 1", ex.message)
    }

    @Test
    fun `입금 계좌가 없으면 예외를 던진다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = TO_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("1:2")

        whenever(accountRepository.findById(FROM_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(FROM_ACCOUNT_ID, BigDecimal("1000.00"))))
        whenever(accountRepository.findById(TO_ACCOUNT_ID)).thenReturn(Optional.empty())

        val ex = assertThrows<NoSuchElementException> {
            transferService.transfer(request)
        }

        assertEquals("입금 계좌를 찾을 수 없습니다: 2", ex.message)
    }

    @Test
    fun `출금 계좌가 CLOSED면 예외를 던진다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = TO_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("1:2")

        whenever(accountRepository.findById(FROM_ACCOUNT_ID))
            .thenReturn(Optional.of(closedAccount(FROM_ACCOUNT_ID, BigDecimal("1000.00"))))
        whenever(accountRepository.findById(TO_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(TO_ACCOUNT_ID, BigDecimal("200.00"))))

        val ex = assertThrows<IllegalStateException> {
            transferService.transfer(request)
        }

        assertEquals("출금 계좌가 해지 상태입니다", ex.message)
    }

    @Test
    fun `입금 계좌가 CLOSED면 예외를 던진다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = TO_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("1:2")

        whenever(accountRepository.findById(FROM_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(FROM_ACCOUNT_ID, BigDecimal("1000.00"))))
        whenever(accountRepository.findById(TO_ACCOUNT_ID))
            .thenReturn(Optional.of(closedAccount(TO_ACCOUNT_ID, BigDecimal("200.00"))))

        val ex = assertThrows<IllegalStateException> {
            transferService.transfer(request)
        }

        assertEquals("입금 계좌가 해지 상태입니다", ex.message)
    }

    @Test
    fun `잔액 부족 시 예외를 던진다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = TO_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("1:2")

        whenever(accountRepository.findById(FROM_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(FROM_ACCOUNT_ID, BigDecimal("100.00"))))
        whenever(accountRepository.findById(TO_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(TO_ACCOUNT_ID, BigDecimal("200.00"))))

        val ex = assertThrows<IllegalStateException> {
            transferService.transfer(request)
        }

        assertEquals("잔액이 부족합니다", ex.message)
    }

    @Test
    fun `송금 시 account-transfer min max 락을 사용한다`() {
        val request = TransferRequest(
            fromAccountId = 9L,
            toAccountId = 3L,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("3:9")

        whenever(accountRepository.findById(9L))
            .thenReturn(Optional.of(activeAccount(9L, BigDecimal("1000.00"))))
        whenever(accountRepository.findById(3L))
            .thenReturn(Optional.of(activeAccount(3L, BigDecimal("200.00"))))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] as Account }
        whenever(transactionNumberGenerator.generate()).thenReturn("TXN-OUT-1", "TXN-IN-1")
        whenever(transactionRepository.save(any<Transaction>())).thenAnswer { it.arguments[0] as Transaction }

        transferService.transfer(request)

        verify(distributedLockService).executeWithLock(eq("account-transfer"), eq("3:9"), any<() -> Any>())
    }

    @Test
    fun `송금 시 거래내역 2건 OUT IN 을 저장한다`() {
        val request = TransferRequest(
            fromAccountId = FROM_ACCOUNT_ID,
            toAccountId = TO_ACCOUNT_ID,
            amount = TRANSFER_AMOUNT
        )
        mockLockExecution("1:2")

        whenever(accountRepository.findById(FROM_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(FROM_ACCOUNT_ID, BigDecimal("1000.00"))))
        whenever(accountRepository.findById(TO_ACCOUNT_ID))
            .thenReturn(Optional.of(activeAccount(TO_ACCOUNT_ID, BigDecimal("200.00"))))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] as Account }
        whenever(transactionNumberGenerator.generate()).thenReturn("TXN-OUT-1", "TXN-IN-1")
        whenever(transactionRepository.save(any<Transaction>())).thenAnswer { it.arguments[0] as Transaction }

        transferService.transfer(request)

        val transactionCaptor = argumentCaptor<Transaction>()
        verify(transactionRepository, times(2)).save(transactionCaptor.capture())

        val types = transactionCaptor.allValues.map { it.type }.toSet()
        assertTrue(types.contains(TransactionType.TRANSFER_OUT))
        assertTrue(types.contains(TransactionType.TRANSFER_IN))
    }

    private fun mockLockExecution(sortedKey: String) {
        whenever(distributedLockService.executeWithLock(eq("account-transfer"), eq(sortedKey), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
    }

    private fun activeAccount(id: Long, balance: BigDecimal) = Account(
        id = id,
        customerId = id,
        accountNumber = "110-123-${id.toString().padStart(6, '0')}",
        product = product,
        balance = balance,
        status = AccountStatus.ACTIVE,
        openedAt = LocalDateTime.now(),
        closedAt = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private fun closedAccount(id: Long, balance: BigDecimal) = Account(
        id = id,
        customerId = id,
        accountNumber = "110-123-${id.toString().padStart(6, '0')}",
        product = product,
        balance = balance,
        status = AccountStatus.CLOSED,
        openedAt = LocalDateTime.now().minusDays(1),
        closedAt = LocalDateTime.now(),
        createdAt = LocalDateTime.now().minusDays(1),
        updatedAt = LocalDateTime.now()
    )
}
