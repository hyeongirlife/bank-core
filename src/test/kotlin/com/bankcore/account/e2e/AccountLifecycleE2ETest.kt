package com.bankcore.account.e2e

import com.bankcore.account.dto.AccountBalanceChangeRequest
import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.AccountStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
class AccountLifecycleE2ETest {

    companion object {
        private const val PRODUCT_CODE = "SAV001"
        private val DEPOSIT_AMOUNT = BigDecimal("1000000000.00")
        private val FIRST_WITHDRAW_AMOUNT = BigDecimal("495000000.00")
        private val REMAINING_WITHDRAW_AMOUNT = BigDecimal("505000000.00")
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `계좌 개설 입금 인출 해지 재시도 시나리오를 검증한다`() {
        val createdAccount = createAccount()

        val depositResponse = changeBalance(createdAccount.id, "deposit", DEPOSIT_AMOUNT)
        assertEquals(DEPOSIT_AMOUNT, depositResponse.balance)
        assertEquals(AccountStatus.ACTIVE, depositResponse.status)

        val firstWithdrawResponse = changeBalance(createdAccount.id, "withdraw", FIRST_WITHDRAW_AMOUNT)
        assertEquals(BigDecimal("505000000.00"), firstWithdrawResponse.balance)
        assertEquals(AccountStatus.ACTIVE, firstWithdrawResponse.status)

        mockMvc.post("/api/accounts/${createdAccount.id}/close")
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { exists() }
            }

        val secondWithdrawResponse = changeBalance(createdAccount.id, "withdraw", REMAINING_WITHDRAW_AMOUNT)
        assertEquals(BigDecimal.ZERO.setScale(2), secondWithdrawResponse.balance)

        mockMvc.post("/api/accounts/${createdAccount.id}/close")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CLOSED") }
                jsonPath("$.closedAt") { exists() }
            }

        mockMvc.post("/api/accounts/${createdAccount.id}/deposit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(AccountBalanceChangeRequest(BigDecimal("1.00")))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { exists() }
        }

        mockMvc.post("/api/accounts/${createdAccount.id}/withdraw") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(AccountBalanceChangeRequest(BigDecimal("1.00")))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { exists() }
        }
    }

    private fun createAccount(): AccountResponse {
        val request = AccountCreateRequest(
            customerId = System.currentTimeMillis(),
            productCode = PRODUCT_CODE
        )

        val result = mockMvc.post("/api/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.status") { value("ACTIVE") }
        }.andReturn()

        return objectMapper.readValue(result.response.contentAsString, AccountResponse::class.java).also {
            assertNotNull(it.id)
        }
    }

    private fun changeBalance(accountId: Long, action: String, amount: BigDecimal): AccountResponse {
        val request = AccountBalanceChangeRequest(amount = amount)

        val result = mockMvc.post("/api/accounts/$accountId/$action") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(accountId) }
        }.andReturn()

        return objectMapper.readValue(result.response.contentAsString, AccountResponse::class.java)
    }
}
