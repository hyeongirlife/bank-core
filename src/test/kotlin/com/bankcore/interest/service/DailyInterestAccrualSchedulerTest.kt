package com.bankcore.interest.service

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import com.bankcore.interest.dto.DailyInterestAccrualResponse
import com.bankcore.product.entity.Product
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class DailyInterestAccrualSchedulerTest {

    @Mock lateinit var accountRepository: AccountRepository
    @Mock lateinit var dailyInterestAccrualService: DailyInterestAccrualService

    companion object {
        private val PAGE_SORT: Sort = Sort.by(Sort.Direction.ASC, "id")
    }

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-02-21T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    private fun newScheduler(): DailyInterestAccrualScheduler {
        return DailyInterestAccrualScheduler(accountRepository, dailyInterestAccrualService, fixedClock)
    }

    @Test
    fun `활성 계좌를 페이지 단위로 순회하며 일별 이자 적립을 실행한다`() {
        val scheduler = newScheduler()
        val product = Product(id = 1L, code = "SAV001", name = "Basic Savings")

        val account1 = Account(
            id = 1L,
            customerId = 10L,
            accountNumber = "100-000-000001",
            product = product,
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            openedAt = LocalDateTime.of(2026, 1, 1, 9, 0)
        )
        val account2 = account1.copy(id = 2L, accountNumber = "100-000-000002")
        val account3 = account1.copy(id = 3L, accountNumber = "100-000-000003")

        whenever(accountRepository.findAllByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(0, 100, PAGE_SORT))))
            .thenReturn(PageImpl(listOf(account1, account2), PageRequest.of(0, 100, PAGE_SORT), 101))
        whenever(accountRepository.findAllByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(1, 100, PAGE_SORT))))
            .thenReturn(PageImpl(listOf(account3), PageRequest.of(1, 100, PAGE_SORT), 101))

        whenever(dailyInterestAccrualService.accrueDailyInterest(any(), any())).thenReturn(
            DailyInterestAccrualResponse(
                accountId = 1L,
                businessDate = LocalDate.of(2026, 2, 21),
                baseRate = BigDecimal("0.0300"),
                spreadRate = BigDecimal("0.0010"),
                preferentialRate = BigDecimal("0.0000"),
                appliedRate = BigDecimal("0.0310"),
                balanceSnapshot = BigDecimal("1000.00"),
                interestAmount = BigDecimal("0.08"),
                alreadyProcessed = false
            )
        )

        scheduler.runDailyAccrual()

        verify(accountRepository).findAllByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(0, 100, PAGE_SORT)))
        verify(accountRepository).findAllByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(1, 100, PAGE_SORT)))

        val requestCaptor = argumentCaptor<DailyInterestAccrualRequest>()
        verify(dailyInterestAccrualService, times(3)).accrueDailyInterest(any(), requestCaptor.capture())
        requestCaptor.allValues.forEach { request ->
            assertEquals(LocalDate.of(2026, 2, 21), request.businessDate)
            assertTrue(request.conditionCodes.isEmpty())
        }
    }

    @Test
    fun `특정 계좌 처리 실패가 발생해도 다음 계좌 처리를 계속한다`() {
        val scheduler = newScheduler()
        val product = Product(id = 1L, code = "SAV001", name = "Basic Savings")

        val account1 = Account(
            id = 11L,
            customerId = 20L,
            accountNumber = "100-000-000011",
            product = product,
            balance = BigDecimal("1000.00"),
            status = AccountStatus.ACTIVE,
            openedAt = LocalDateTime.of(2026, 1, 1, 9, 0)
        )
        val account2 = account1.copy(id = 12L, accountNumber = "100-000-000012")

        whenever(accountRepository.findAllByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(0, 100, PAGE_SORT))))
            .thenReturn(PageImpl(listOf(account1, account2), PageRequest.of(0, 100, PAGE_SORT), 2))

        whenever(dailyInterestAccrualService.accrueDailyInterest(eq(11L), any()))
            .thenThrow(IllegalStateException("기준금리 없음"))
        whenever(dailyInterestAccrualService.accrueDailyInterest(eq(12L), any())).thenReturn(
            DailyInterestAccrualResponse(
                accountId = 12L,
                businessDate = LocalDate.of(2026, 2, 21),
                baseRate = BigDecimal("0.0300"),
                spreadRate = BigDecimal("0.0010"),
                preferentialRate = BigDecimal("0.0000"),
                appliedRate = BigDecimal("0.0310"),
                balanceSnapshot = BigDecimal("1000.00"),
                interestAmount = BigDecimal("0.08"),
                alreadyProcessed = false
            )
        )

        assertDoesNotThrow {
            scheduler.runDailyAccrual()
        }

        verify(dailyInterestAccrualService).accrueDailyInterest(eq(11L), any())
        verify(dailyInterestAccrualService).accrueDailyInterest(eq(12L), any())
    }
}
