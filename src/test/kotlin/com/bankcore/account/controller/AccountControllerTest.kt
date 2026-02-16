package com.bankcore.account.controller

import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.service.AccountService
import com.bankcore.common.idempotency.IdempotencyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(AccountController::class)
class AccountControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @MockitoBean lateinit var accountService: AccountService
    @MockitoBean lateinit var idempotencyService: IdempotencyService

    @Test
    fun `계좌 개설 요청 시 201을 반환한다`() {
        val request = AccountCreateRequest(customerId = 1L, productCode = "SAV001")
        val response = AccountResponse(
            id = 1L,
            customerId = 1L,
            accountNumber = "110-123-456789",
            productCode = "SAV001",
            productName = "Basic Savings",
            balance = BigDecimal("0.00"),
            status = AccountStatus.ACTIVE,
            openedAt = LocalDateTime.now(),
            closedAt = null
        )

        whenever(accountService.createAccount(any())).thenReturn(response)

        mockMvc.post("/api/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.accountNumber") { value("110-123-456789") }
            jsonPath("$.productCode") { value("SAV001") }
            jsonPath("$.balance") { value(0.00) }
            jsonPath("$.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `존재하지 않는 상품 코드로 요청 시 400을 반환한다`() {
        val request = AccountCreateRequest(customerId = 1L, productCode = "INVALID")

        whenever(accountService.createAccount(any()))
            .thenThrow(IllegalArgumentException("상품을 찾을 수 없습니다: INVALID"))

        mockMvc.post("/api/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `빈 상품 코드로 요청 시 400을 반환한다`() {
        val request = AccountCreateRequest(customerId = 1L, productCode = "")

        mockMvc.post("/api/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `음수 고객 ID로 요청 시 400을 반환한다`() {
        val request = AccountCreateRequest(customerId = -1L, productCode = "SAV001")

        mockMvc.post("/api/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
