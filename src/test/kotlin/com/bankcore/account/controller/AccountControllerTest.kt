package com.bankcore.account.controller

import com.bankcore.account.dto.AccountBalanceChangeRequest
import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.service.AccountService
import com.bankcore.common.idempotency.IdempotencyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
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

    @Test
    fun `계좌 ID로 조회 시 200을 반환한다`() {
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

        whenever(accountService.getAccount(1L)).thenReturn(response)

        mockMvc.get("/api/accounts/1")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(1) }
                jsonPath("$.accountNumber") { value("110-123-456789") }
                jsonPath("$.status") { value("ACTIVE") }
            }
    }

    @Test
    fun `존재하지 않는 계좌 ID 조회 시 404를 반환한다`() {
        whenever(accountService.getAccount(999L))
            .thenThrow(NoSuchElementException("계좌를 찾을 수 없습니다: 999"))

        mockMvc.get("/api/accounts/999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `계좌 입금 요청 시 200을 반환한다`() {
        val request = AccountBalanceChangeRequest(amount = BigDecimal("1000000000.00"))
        val response = AccountResponse(
            id = 1L,
            customerId = 1L,
            accountNumber = "110-123-456789",
            productCode = "SAV001",
            productName = "Basic Savings",
            balance = BigDecimal("1000000000.00"),
            status = AccountStatus.ACTIVE,
            openedAt = LocalDateTime.now(),
            closedAt = null
        )

        whenever(accountService.deposit(eq(1L), any())).thenReturn(response)

        mockMvc.post("/api/accounts/1/deposit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.balance") { value(1000000000.00) }
            jsonPath("$.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `계좌 출금 요청 시 200을 반환한다`() {
        val request = AccountBalanceChangeRequest(amount = BigDecimal("495000000.00"))
        val response = AccountResponse(
            id = 1L,
            customerId = 1L,
            accountNumber = "110-123-456789",
            productCode = "SAV001",
            productName = "Basic Savings",
            balance = BigDecimal("505000000.00"),
            status = AccountStatus.ACTIVE,
            openedAt = LocalDateTime.now(),
            closedAt = null
        )

        whenever(accountService.withdraw(eq(1L), any())).thenReturn(response)

        mockMvc.post("/api/accounts/1/withdraw") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.balance") { value(505000000.00) }
            jsonPath("$.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `계좌 해지 요청 시 200을 반환한다`() {
        val response = AccountResponse(
            id = 1L,
            customerId = 1L,
            accountNumber = "110-123-456789",
            productCode = "SAV001",
            productName = "Basic Savings",
            balance = BigDecimal("0.00"),
            status = AccountStatus.CLOSED,
            openedAt = LocalDateTime.now().minusDays(1),
            closedAt = LocalDateTime.now()
        )

        whenever(accountService.closeAccount(1L)).thenReturn(response)

        mockMvc.post("/api/accounts/1/close")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(1) }
                jsonPath("$.status") { value("CLOSED") }
            }
    }

    @Test
    fun `존재하지 않는 계좌 해지 시 404를 반환한다`() {
        whenever(accountService.closeAccount(999L))
            .thenThrow(NoSuchElementException("계좌를 찾을 수 없습니다: 999"))

        mockMvc.post("/api/accounts/999/close")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `이미 해지된 계좌 해지 시 409를 반환한다`() {
        whenever(accountService.closeAccount(1L))
            .thenThrow(IllegalStateException("이미 해지된 계좌입니다"))

        mockMvc.post("/api/accounts/1/close")
            .andExpect {
                status { isConflict() }
            }
    }

    @Test
    fun `잔액이 남아있는 계좌 해지 시 409를 반환한다`() {
        whenever(accountService.closeAccount(1L))
            .thenThrow(IllegalStateException("잔액이 남아있는 계좌는 해지할 수 없습니다"))

        mockMvc.post("/api/accounts/1/close")
            .andExpect {
                status { isConflict() }
            }
    }
}
