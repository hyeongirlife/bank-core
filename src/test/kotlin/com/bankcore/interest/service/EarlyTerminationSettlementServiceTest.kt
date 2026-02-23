package com.bankcore.interest.service

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import com.bankcore.interest.dto.DailyInterestAccrualResponse
import com.bankcore.interest.entity.InterestLog
import com.bankcore.interest.entity.InterestSettlement
import com.bankcore.interest.entity.InterestSettlementType
import com.bankcore.interest.repository.InterestLogRepository
import com.bankcore.interest.repository.InterestSettlementRepository
import com.bankcore.product.entity.Product
import com.bankcore.transaction.entity.Transaction
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.bankcore.transaction.service.TransactionNumberGenerator
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class EarlyTerminationSettlementServiceTest {

    @Mock lateinit var dailyInterestAccrualService: DailyInterestAccrualService
    @Mock lateinit var interestLogRepository: InterestLogRepository
    @Mock lateinit var interestSettlementRepository: InterestSettlementRepository
    @Mock lateinit var transactionRepository: TransactionRepository
    @Mock lateinit var transactionNumberGenerator: TransactionNumberGenerator
    @Mock lateinit var distributedLockService: DistributedLockService
    @Mock lateinit var accountRepository: AccountRepository

    lateinit var earlyTerminationSettlementService: EarlyTerminationSettlementService

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-02-21T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val product = Product(id = 1L, code = "SAV001", name = "Basic Savings")

    @BeforeEach
    fun setUp() {
        earlyTerminationSettlementService = EarlyTerminationSettlementService(
            dailyInterestAccrualService = dailyInterestAccrualService,
            interestLogRepository = interestLogRepository,
            interestSettlementRepository = interestSettlementRepository,
            transactionRepository = transactionRepository,
            transactionNumberGenerator = transactionNumberGenerator,
            distributedLockService = distributedLockService,
            clock = fixedClock
        )
    }

    @Test
    fun `중도해지 최초 정산 시 해지일 이자 적립 후 정산 로그와 거래를 생성한다`() {
        val businessDate = LocalDate.of(2026, 2, 21)
        val account = activeAccount(maturityDate = businessDate.plusDays(7))
        val accrualResponse = DailyInterestAccrualResponse(
            accountId = account.id!!,
            businessDate = businessDate,
            baseRate = BigDecimal("0.0300"),
            spreadRate = BigDecimal("0.0010"),
            preferentialRate = BigDecimal("0.0000"),
            appliedRate = BigDecimal("0.0310"),
            balanceSnapshot = BigDecimal("1000000.00"),
            interestAmount = BigDecimal("84.93"),
            alreadyProcessed = false
        )

        whenever(distributedLockService.executeWithLock(eq("interest-settlement"), eq("1:EARLY_TERMINATION"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(interestSettlementRepository.findByAccountIdAndSettlementType(account.id!!, InterestSettlementType.EARLY_TERMINATION))
            .thenReturn(null)
        whenever(dailyInterestAccrualService.accrueDailyInterest(account.id!!, DailyInterestAccrualRequest(businessDate, emptyList())))
            .thenReturn(accrualResponse)
        whenever(interestLogRepository.sumInterestAmountByAccountIdAndBusinessDateBetween(account.id!!, account.openedAt.toLocalDate(), businessDate))
            .thenReturn(BigDecimal("95.999"))
        whenever(interestSettlementRepository.saveAndFlush(any<InterestSettlement>())).thenAnswer { it.arguments[0] as InterestSettlement }
        whenever(transactionNumberGenerator.generate()).thenReturn("TXN-SETTLEMENT-1")
        whenever(transactionRepository.save(any<Transaction>())).thenAnswer { it.arguments[0] as Transaction }

        val result = earlyTerminationSettlementService.settleOnClose(account, businessDate)

        assertTrue(result.settled)
        assertEquals(InterestSettlementType.EARLY_TERMINATION, result.settlementType)
        assertEquals(BigDecimal("95.99"), result.interestAmount)
        assertEquals(2, result.interestAmount.scale())
        verify(transactionRepository).save(any<Transaction>())
    }

    @Test
    fun `동일 계좌 중도해지 정산 재시도 시 기존 로그를 반환한다`() {
        val businessDate = LocalDate.of(2026, 2, 21)
        val account = activeAccount(maturityDate = businessDate.plusDays(5))
        val existingSettlement = InterestSettlement(
            id = 11L,
            account = account,
            settlementType = InterestSettlementType.EARLY_TERMINATION,
            businessDate = businessDate,
            interestAmount = BigDecimal("10.00")
        )

        whenever(distributedLockService.executeWithLock(eq("interest-settlement"), eq("1:EARLY_TERMINATION"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(interestSettlementRepository.findByAccountIdAndSettlementType(account.id!!, InterestSettlementType.EARLY_TERMINATION))
            .thenReturn(existingSettlement)

        val result = earlyTerminationSettlementService.settleOnClose(account, businessDate)

        assertTrue(result.alreadyProcessed)
        assertEquals(BigDecimal("10.00"), result.interestAmount)
        verify(dailyInterestAccrualService, never()).accrueDailyInterest(any(), any())
        verify(transactionRepository, never()).save(any<Transaction>())
    }

    @Test
    fun `정산 저장 충돌 시 기존 정산 로그를 재조회해 반환한다`() {
        val businessDate = LocalDate.of(2026, 2, 21)
        val account = activeAccount(maturityDate = businessDate.plusDays(3))
        val existingAfterConflict = InterestSettlement(
            id = 99L,
            account = account,
            settlementType = InterestSettlementType.EARLY_TERMINATION,
            businessDate = businessDate,
            interestAmount = BigDecimal("20.00")
        )

        whenever(distributedLockService.executeWithLock(eq("interest-settlement"), eq("1:EARLY_TERMINATION"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(interestSettlementRepository.findByAccountIdAndSettlementType(account.id!!, InterestSettlementType.EARLY_TERMINATION))
            .thenReturn(null)
            .thenReturn(existingAfterConflict)
        whenever(dailyInterestAccrualService.accrueDailyInterest(account.id!!, DailyInterestAccrualRequest(businessDate, emptyList())))
            .thenReturn(
                DailyInterestAccrualResponse(
                    accountId = account.id!!,
                    businessDate = businessDate,
                    baseRate = BigDecimal("0.0300"),
                    spreadRate = BigDecimal("0.0010"),
                    preferentialRate = BigDecimal("0.0000"),
                    appliedRate = BigDecimal("0.0310"),
                    balanceSnapshot = BigDecimal("1000000.00"),
                    interestAmount = BigDecimal("84.93"),
                    alreadyProcessed = false
                )
            )
        whenever(interestLogRepository.sumInterestAmountByAccountIdAndBusinessDateBetween(account.id!!, account.openedAt.toLocalDate(), businessDate))
            .thenReturn(BigDecimal("20.00"))
        whenever(interestSettlementRepository.saveAndFlush(any<InterestSettlement>()))
            .thenThrow(DataIntegrityViolationException("duplicate"))

        val result = earlyTerminationSettlementService.settleOnClose(account, businessDate)

        assertTrue(result.alreadyProcessed)
        assertEquals(BigDecimal("20.00"), result.interestAmount)
        verify(transactionRepository, never()).save(any<Transaction>())
    }

    @Test
    fun `만기 후 해지는 중도해지 정산을 수행하지 않는다`() {
        val businessDate = LocalDate.of(2026, 2, 21)
        val account = activeAccount(maturityDate = businessDate.minusDays(1))

        val result = earlyTerminationSettlementService.settleOnClose(account, businessDate)

        assertTrue(result.skipped)
        verify(interestSettlementRepository, never()).saveAndFlush(any<InterestSettlement>())
        verify(transactionRepository, never()).save(any<Transaction>())
    }

    @Test
    fun `중도해지 정산 실패 시 예외를 전파한다`() {
        val businessDate = LocalDate.of(2026, 2, 21)
        val account = activeAccount(maturityDate = businessDate.plusDays(10))

        whenever(distributedLockService.executeWithLock(eq("interest-settlement"), eq("1:EARLY_TERMINATION"), any<() -> Any>()))
            .thenAnswer { (it.arguments[2] as () -> Any).invoke() }
        whenever(interestSettlementRepository.findByAccountIdAndSettlementType(account.id!!, InterestSettlementType.EARLY_TERMINATION))
            .thenReturn(null)
        whenever(dailyInterestAccrualService.accrueDailyInterest(account.id!!, DailyInterestAccrualRequest(businessDate, emptyList())))
            .thenThrow(IllegalStateException("해지일 이자 적립 실패"))

        val ex = assertThrows<IllegalStateException> {
            earlyTerminationSettlementService.settleOnClose(account, businessDate)
        }

        assertEquals("해지일 이자 적립 실패", ex.message)
        verify(interestSettlementRepository, never()).saveAndFlush(any<InterestSettlement>())
        verify(transactionRepository, never()).save(any<Transaction>())
    }

    private fun activeAccount(maturityDate: LocalDate?) = Account(
        id = 1L,
        customerId = 1L,
        accountNumber = "110-123-456789",
        product = product,
        balance = BigDecimal.ZERO.setScale(2),
        status = AccountStatus.ACTIVE,
        maturityDate = maturityDate,
        openedAt = LocalDateTime.of(2026, 2, 1, 9, 0),
        closedAt = null
    )
}
