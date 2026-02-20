package com.bankcore.transfer.controller

import com.bankcore.common.idempotency.IdempotencyService
import com.bankcore.transfer.dto.TransferRequest
import com.bankcore.transfer.dto.TransferResponse
import com.bankcore.transfer.service.TransferService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.hamcrest.Matchers.containsString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(TransferController::class)
class TransferControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @MockitoBean lateinit var transferService: TransferService
    @MockitoBean lateinit var idempotencyService: IdempotencyService

    @Test
    fun `송금 요청 시 200을 반환한다`() {
        val request = TransferRequest(
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00")
        )
        val response = TransferResponse(
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00"),
            fromBalance = BigDecimal("900.00"),
            toBalance = BigDecimal("300.00"),
            transferredAt = LocalDateTime.now()
        )

        whenever(transferService.transfer(any())).thenReturn(response)

        mockMvc.post("/api/transfers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.fromAccountId") { value(1) }
            jsonPath("$.toAccountId") { value(2) }
            jsonPath("$.amount") { value(100.00) }
        }
    }

    @Test
    fun `동일 계좌 송금 시 400을 반환한다`() {
        val request = TransferRequest(
            fromAccountId = 1L,
            toAccountId = 1L,
            amount = BigDecimal("100.00")
        )

        whenever(transferService.transfer(any())).thenThrow(IllegalArgumentException("동일 계좌로는 송금할 수 없습니다"))

        mockMvc.post("/api/transfers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("동일 계좌")) }
        }
    }

    @Test
    fun `미존재 계좌 송금 시 404를 반환한다`() {
        val request = TransferRequest(
            fromAccountId = 1L,
            toAccountId = 999L,
            amount = BigDecimal("100.00")
        )

        whenever(transferService.transfer(any())).thenThrow(NoSuchElementException("입금 계좌를 찾을 수 없습니다: 999"))

        mockMvc.post("/api/transfers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value(containsString("입금 계좌")) }
        }
    }

    @Test
    fun `잔액 부족 또는 CLOSED 계좌 송금 시 409를 반환한다`() {
        val request = TransferRequest(
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100000.00")
        )

        whenever(transferService.transfer(any())).thenThrow(IllegalStateException("잔액이 부족합니다"))

        mockMvc.post("/api/transfers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value(containsString("잔액")) }
        }
    }

    @Test
    fun `동시성 충돌 시 409를 반환한다`() {
        val request = TransferRequest(
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.00")
        )

        whenever(transferService.transfer(any())).thenThrow(
            ObjectOptimisticLockingFailureException("com.bankcore.account.entity.Account", 1L)
        )

        mockMvc.post("/api/transfers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value(containsString("동시성")) }
        }
    }

    @Test
    fun `소수점 둘째 자리 초과 금액 요청 시 400을 반환한다`() {
        val request = TransferRequest(
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = BigDecimal("100.001")
        )

        mockMvc.post("/api/transfers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("digits")) }
        }
    }
}
