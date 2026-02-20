package com.bankcore.interest.controller

import com.bankcore.common.idempotency.IdempotencyService
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import com.bankcore.interest.dto.DailyInterestAccrualResponse
import com.bankcore.interest.service.DailyInterestAccrualService
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(InterestController::class)
class InterestControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @MockitoBean lateinit var dailyInterestAccrualService: DailyInterestAccrualService
    @MockitoBean lateinit var idempotencyService: IdempotencyService

    @Test
    fun `일별 이자 적립 요청 시 200을 반환한다`() {
        val accountId = 1L
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = listOf("SALARY_TRANSFER", "CARD_USAGE")
        )
        val response = DailyInterestAccrualResponse(
            accountId = accountId,
            businessDate = request.businessDate,
            baseRate = BigDecimal("0.0300"),
            spreadRate = BigDecimal("0.0010"),
            preferentialRate = BigDecimal("0.0020"),
            appliedRate = BigDecimal("0.0330"),
            balanceSnapshot = BigDecimal("1000000.00"),
            interestAmount = BigDecimal("90.41"),
            alreadyProcessed = false
        )

        whenever(dailyInterestAccrualService.accrueDailyInterest(eq(accountId), any())).thenReturn(response)

        mockMvc.post("/api/accounts/$accountId/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.accountId") { value(1) }
            jsonPath("$.alreadyProcessed") { value(false) }
            jsonPath("$.interestAmount") { value(90.41) }
        }
    }

    @Test
    fun `영업일이 미래면 400을 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.now().plusDays(1),
            conditionCodes = listOf("SALARY_TRANSFER")
        )

        mockMvc.post("/api/accounts/1/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("영업일")) }
        }
    }

    @Test
    fun `빈 조건코드 포함 요청이면 400을 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = listOf("SALARY_TRANSFER", "")
        )

        mockMvc.post("/api/accounts/1/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("조건코드")) }
        }
    }

    @Test
    fun `중복 조건코드 포함 요청이면 400을 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = listOf("SALARY_TRANSFER", "SALARY_TRANSFER")
        )

        mockMvc.post("/api/accounts/1/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("중복")) }
        }
    }

    @Test
    fun `허용되지 않은 조건코드 패턴이면 400을 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = listOf("salary_transfer")
        )

        mockMvc.post("/api/accounts/1/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("영문 대문자")) }
        }
    }

    @Test
    fun `조건코드 개수 초과면 400을 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = ('A'..'U').map { "CODE_$it" }
        )

        mockMvc.post("/api/accounts/1/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("최대 20개")) }
        }
    }

    @Test
    fun `존재하지 않는 계좌면 404를 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = listOf("SALARY_TRANSFER")
        )

        whenever(dailyInterestAccrualService.accrueDailyInterest(eq(999L), any()))
            .thenThrow(NoSuchElementException("계좌를 찾을 수 없습니다: 999"))

        mockMvc.post("/api/accounts/999/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value(containsString("계좌")) }
        }
    }

    @Test
    fun `금리 미설정이면 409를 반환한다`() {
        val request = DailyInterestAccrualRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = listOf("SALARY_TRANSFER")
        )

        whenever(dailyInterestAccrualService.accrueDailyInterest(eq(1L), any()))
            .thenThrow(IllegalStateException("기준금리가 설정되지 않았습니다"))

        mockMvc.post("/api/accounts/1/interests/daily-accrual") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value(containsString("금리")) }
        }
    }
}
