package com.bankcore.account.e2e

import com.bankcore.account.dto.AccountBalanceChangeRequest
import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.interest.entity.InterestSettlementType
import com.bankcore.interest.repository.InterestSettlementRepository
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import com.bankcore.rate.entity.BaseRate
import com.bankcore.rate.entity.SpreadRate
import com.bankcore.rate.repository.BaseRateRepository
import com.bankcore.rate.repository.SpreadRateRepository
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

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

    @Autowired
    lateinit var productRepository: ProductRepository

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var interestSettlementRepository: InterestSettlementRepository

    @Autowired
    lateinit var transactionRepository: TransactionRepository

    @Autowired
    lateinit var baseRateRepository: BaseRateRepository

    @Autowired
    lateinit var spreadRateRepository: SpreadRateRepository

    private lateinit var earlyTerminationProductCode: String

    @BeforeEach
    fun setUp() {
        earlyTerminationProductCode = "EARLY-${System.nanoTime()}"
        val product = productRepository.save(
            Product(
                code = earlyTerminationProductCode,
                name = "Early Termination Savings"
            )
        )

        val businessDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
        if (baseRateRepository.findByBusinessDate(businessDate) == null) {
            baseRateRepository.save(
                BaseRate(
                    businessDate = businessDate,
                    rate = BigDecimal("0.0300")
                )
            )
        }

        if (spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue(earlyTerminationProductCode, businessDate) == null) {
            spreadRateRepository.save(
                SpreadRate(
                    product = product,
                    businessDate = businessDate,
                    rate = BigDecimal("0.0010"),
                    isActive = true
                )
            )
        }
    }

    @Test
    fun `계좌 개설 입금 인출 해지 재시도 시나리오를 검증한다`() {
        val createdAccount = createAccount(productCode = PRODUCT_CODE)

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

    private fun createAccount(productCode: String = PRODUCT_CODE, maturityDate: LocalDate? = null): AccountResponse {
        val request = AccountCreateRequest(
            customerId = System.currentTimeMillis(),
            productCode = productCode,
            maturityDate = maturityDate
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

    @Test
    fun `중도해지 계좌를 동일 멱등키로 재시도해도 정산은 1건만 생성된다`() {
        val maturityDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(30)
        val createdAccount = createAccount(
            productCode = earlyTerminationProductCode,
            maturityDate = maturityDate
        )

        changeBalance(createdAccount.id, "deposit", BigDecimal("1000000.00"))
        changeBalance(createdAccount.id, "withdraw", BigDecimal("1000000.00"))

        val idempotencyKey = "close-early-${createdAccount.id}"
        val firstClose = mockMvc.post("/api/accounts/${createdAccount.id}/close") {
            header("Idempotency-Key", idempotencyKey)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CLOSED") }
        }.andReturn()

        val secondClose = mockMvc.post("/api/accounts/${createdAccount.id}/close") {
            header("Idempotency-Key", idempotencyKey)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        assertEquals(firstClose.response.contentAsString, secondClose.response.contentAsString)

        val account = accountRepository.findById(createdAccount.id).orElseThrow()
        val settlements = interestSettlementRepository.findAllByAccountIdAndSettlementType(
            createdAccount.id,
            InterestSettlementType.EARLY_TERMINATION
        )
        val settlementTransactions = transactionRepository.findAllByAccountIdAndType(
            createdAccount.id,
            TransactionType.INTEREST_SETTLEMENT
        )

        assertEquals(AccountStatus.CLOSED, account.status)
        assertEquals(1, settlements.size)
        assertEquals(1, settlementTransactions.size)
        assertEquals(LocalDate.now(ZoneId.of("Asia/Seoul")), settlements.first().businessDate)
        assertTrue(settlements.first().interestAmount.scale() == 2)
    }

    @Test
    fun `만기 경과 계좌 해지는 중도해지 정산을 생성하지 않는다`() {
        val maturedAccount = createAccount(
            productCode = earlyTerminationProductCode,
            maturityDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)
        )

        changeBalance(maturedAccount.id, "deposit", BigDecimal("50000.00"))
        changeBalance(maturedAccount.id, "withdraw", BigDecimal("50000.00"))

        mockMvc.post("/api/accounts/${maturedAccount.id}/close")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CLOSED") }
            }

        val settlements = interestSettlementRepository.findAllByAccountIdAndSettlementType(
            maturedAccount.id,
            InterestSettlementType.EARLY_TERMINATION
        )
        val settlementTransactions = transactionRepository.findAllByAccountIdAndType(
            maturedAccount.id,
            TransactionType.INTEREST_SETTLEMENT
        )

        assertTrue(settlements.isEmpty())
        assertTrue(settlementTransactions.isEmpty())
    }
}
