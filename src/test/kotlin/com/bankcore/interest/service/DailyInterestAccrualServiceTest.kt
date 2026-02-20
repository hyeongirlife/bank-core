package com.bankcore.interest.service

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import com.bankcore.interest.entity.InterestLog
import com.bankcore.interest.repository.InterestLogRepository
import com.bankcore.product.entity.Product
import com.bankcore.rate.entity.BaseRate
import com.bankcore.rate.entity.PreferentialRate
import com.bankcore.rate.entity.SpreadRate
import com.bankcore.rate.repository.BaseRateRepository
import com.bankcore.rate.repository.PreferentialRateRepository
import com.bankcore.rate.repository.SpreadRateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DailyInterestAccrualServiceTest {

    @Mock lateinit var accountRepository: AccountRepository
    @Mock lateinit var baseRateRepository: BaseRateRepository
    @Mock lateinit var spreadRateRepository: SpreadRateRepository
    @Mock lateinit var preferentialRateRepository: PreferentialRateRepository
    @Mock lateinit var interestLogRepository: InterestLogRepository
    @Mock lateinit var distributedLockService: DistributedLockService
    @Mock lateinit var interestAmountCalculator: InterestAmountCalculator

    @InjectMocks lateinit var dailyInterestAccrualService: DailyInterestAccrualService

    private val businessDate = LocalDate.of(2026, 2, 20)
    private val product = Product(id = 1L, code = "SAV001", name = "Basic Savings")
    private val account = Account(
        id = 1L,
        customerId = 1L,
        accountNumber = "110-123-456789",
        product = product,
        balance = BigDecimal("1000000.00"),
        status = AccountStatus.ACTIVE,
        openedAt = LocalDateTime.of(2026, 2, 1, 9, 0),
        closedAt = null
    )

    @Test
    fun `최초 처리 시 이자 로그를 저장하고 alreadyProcessed false를 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = businessDate,
            conditionCodes = listOf("SALARY_TRANSFER")
        )

        whenever(interestLogRepository.findByAccountIdAndBusinessDate(1L, businessDate)).thenReturn(null)
        whenever(distributedLockService.executeWithLock(eq("interest-accrual"), eq("1:2026-02-20"), any<() -> Any>()))
            .thenAnswer { invocation ->
                val action = invocation.getArgument<() -> Any>(2)
                action.invoke()
            }
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(account))
        whenever(baseRateRepository.findByBusinessDate(businessDate)).thenReturn(
            BaseRate(id = 1L, businessDate = businessDate, rate = BigDecimal("0.0300"))
        )
        whenever(spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue("SAV001", businessDate)).thenReturn(
            SpreadRate(id = 1L, product = product, businessDate = businessDate, rate = BigDecimal("0.0010"), isActive = true)
        )
        whenever(preferentialRateRepository.findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
            "SAV001",
            listOf("SALARY_TRANSFER"),
            businessDate
        )).thenReturn(
            listOf(
                PreferentialRate(
                    id = 1L,
                    product = product,
                    conditionCode = "SALARY_TRANSFER",
                    businessDate = businessDate,
                    rate = BigDecimal("0.0020"),
                    isActive = true
                )
            )
        )
        whenever(interestAmountCalculator.calculateAppliedRate(
            BigDecimal("0.0300"),
            BigDecimal("0.0010"),
            BigDecimal("0.0020")
        )).thenReturn(BigDecimal("0.0330"))
        whenever(interestAmountCalculator.calculateDailyInterest(BigDecimal("1000000.00"), BigDecimal("0.0330")))
            .thenReturn(BigDecimal("90.41"))
        whenever(interestLogRepository.saveAndFlush(any<InterestLog>())).thenAnswer { it.arguments[0] as InterestLog }

        val response = dailyInterestAccrualService.accrueDailyInterest(1L, request)

        assertEquals(false, response.alreadyProcessed)
        assertEquals(BigDecimal("0.0330"), response.appliedRate)
        assertEquals(BigDecimal("90.41"), response.interestAmount)
        verify(distributedLockService).executeWithLock(eq("interest-accrual"), eq("1:2026-02-20"), any<() -> Any>())
    }

    @Test
    fun `기존 로그가 있으면 alreadyProcessed true와 기존 로그를 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = businessDate,
            conditionCodes = listOf("SALARY_TRANSFER")
        )
        val existing = InterestLog(
            id = 10L,
            account = account,
            businessDate = businessDate,
            baseRate = BigDecimal("0.0300"),
            spreadRate = BigDecimal("0.0010"),
            preferentialRate = BigDecimal("0.0020"),
            appliedRate = BigDecimal("0.0330"),
            balanceSnapshot = BigDecimal("1000000.00"),
            interestAmount = BigDecimal("90.41")
        )

        whenever(interestLogRepository.findByAccountIdAndBusinessDate(1L, businessDate)).thenReturn(existing)

        val response = dailyInterestAccrualService.accrueDailyInterest(1L, request)

        assertTrue(response.alreadyProcessed)
        assertEquals(BigDecimal("90.41"), response.interestAmount)
        verify(distributedLockService, never()).executeWithLock(any(), any(), any<() -> Any>())
    }

    @Test
    fun `해지된 계좌면 예외를 던진다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = businessDate,
            conditionCodes = listOf("SALARY_TRANSFER")
        )
        val closedAccount = account.copy(
            status = AccountStatus.CLOSED,
            closedAt = LocalDateTime.of(2026, 2, 10, 9, 0)
        )

        whenever(interestLogRepository.findByAccountIdAndBusinessDate(1L, businessDate)).thenReturn(null)
        whenever(distributedLockService.executeWithLock(eq("interest-accrual"), eq("1:2026-02-20"), any<() -> Any>()))
            .thenAnswer { invocation ->
                val action = invocation.getArgument<() -> Any>(2)
                action.invoke()
            }
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(closedAccount))

        val ex = assertThrows<IllegalStateException> {
            dailyInterestAccrualService.accrueDailyInterest(1L, request)
        }

        assertEquals("해지된 계좌에는 일별 이자를 적립할 수 없습니다", ex.message)
    }

    @Test
    fun `기준금리 미설정이면 예외를 던진다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = businessDate,
            conditionCodes = listOf("SALARY_TRANSFER")
        )

        whenever(interestLogRepository.findByAccountIdAndBusinessDate(1L, businessDate)).thenReturn(null)
        whenever(distributedLockService.executeWithLock(eq("interest-accrual"), eq("1:2026-02-20"), any<() -> Any>()))
            .thenAnswer { invocation ->
                val action = invocation.getArgument<() -> Any>(2)
                action.invoke()
            }
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(account))
        whenever(baseRateRepository.findByBusinessDate(businessDate)).thenReturn(null)

        val ex = assertThrows<IllegalStateException> {
            dailyInterestAccrualService.accrueDailyInterest(1L, request)
        }

        assertEquals("기준금리가 설정되지 않았습니다: 2026-02-20", ex.message)
    }

    @Test
    fun `락 획득 이후 재조회에서 기존 로그를 찾으면 existing을 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = businessDate,
            conditionCodes = listOf("SALARY_TRANSFER")
        )
        val existing = InterestLog(
            id = 10L,
            account = account,
            businessDate = businessDate,
            baseRate = BigDecimal("0.0300"),
            spreadRate = BigDecimal("0.0010"),
            preferentialRate = BigDecimal("0.0020"),
            appliedRate = BigDecimal("0.0330"),
            balanceSnapshot = BigDecimal("1000000.00"),
            interestAmount = BigDecimal("90.41")
        )

        whenever(interestLogRepository.findByAccountIdAndBusinessDate(1L, businessDate))
            .thenReturn(null)
            .thenReturn(existing)
        whenever(distributedLockService.executeWithLock(eq("interest-accrual"), eq("1:2026-02-20"), any<() -> Any>()))
            .thenAnswer { invocation ->
                val action = invocation.getArgument<() -> Any>(2)
                action.invoke()
            }

        val response = dailyInterestAccrualService.accrueDailyInterest(1L, request)

        assertTrue(response.alreadyProcessed)
        verify(interestLogRepository, times(2)).findByAccountIdAndBusinessDate(1L, businessDate)
    }

    @Test
    fun `저장 시 유니크 충돌이 발생하면 기존 로그를 재조회해 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = businessDate,
            conditionCodes = listOf("SALARY_TRANSFER")
        )
        val existing = InterestLog(
            id = 11L,
            account = account,
            businessDate = businessDate,
            baseRate = BigDecimal("0.0300"),
            spreadRate = BigDecimal("0.0010"),
            preferentialRate = BigDecimal("0.0020"),
            appliedRate = BigDecimal("0.0330"),
            balanceSnapshot = BigDecimal("1000000.00"),
            interestAmount = BigDecimal("90.41")
        )

        whenever(interestLogRepository.findByAccountIdAndBusinessDate(1L, businessDate))
            .thenReturn(null)
            .thenReturn(null)
            .thenReturn(existing)
        whenever(distributedLockService.executeWithLock(eq("interest-accrual"), eq("1:2026-02-20"), any<() -> Any>()))
            .thenAnswer { invocation ->
                val action = invocation.getArgument<() -> Any>(2)
                action.invoke()
            }
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(account))
        whenever(baseRateRepository.findByBusinessDate(businessDate)).thenReturn(
            BaseRate(id = 1L, businessDate = businessDate, rate = BigDecimal("0.0300"))
        )
        whenever(spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue("SAV001", businessDate)).thenReturn(
            SpreadRate(id = 1L, product = product, businessDate = businessDate, rate = BigDecimal("0.0010"), isActive = true)
        )
        whenever(preferentialRateRepository.findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
            "SAV001",
            listOf("SALARY_TRANSFER"),
            businessDate
        )).thenReturn(
            listOf(
                PreferentialRate(
                    id = 1L,
                    product = product,
                    conditionCode = "SALARY_TRANSFER",
                    businessDate = businessDate,
                    rate = BigDecimal("0.0020"),
                    isActive = true
                )
            )
        )
        whenever(interestAmountCalculator.calculateAppliedRate(any(), any(), any())).thenReturn(BigDecimal("0.0330"))
        whenever(interestAmountCalculator.calculateDailyInterest(any(), any())).thenReturn(BigDecimal("90.41"))
        whenever(interestLogRepository.saveAndFlush(any<InterestLog>())).thenThrow(DataIntegrityViolationException("unique"))

        val response = dailyInterestAccrualService.accrueDailyInterest(1L, request)

        assertTrue(response.alreadyProcessed)
        assertEquals(BigDecimal("90.41"), response.interestAmount)
    }
}
