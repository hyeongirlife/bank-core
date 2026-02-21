package com.bankcore.transfer.e2e

import com.bankcore.account.dto.AccountBalanceChangeRequest
import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.Account
import com.bankcore.account.repository.AccountRepository
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import com.bankcore.testsupport.TestcontainersIntegrationBase
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.bankcore.transfer.dto.TransferRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future

@TestPropertySource(properties = ["transfer.lock.strategy=DISTRIBUTED"])
class TransferConcurrencyReproductionTest : TestcontainersIntegrationBase() {

    companion object {
        private const val PRODUCT_CODE = "TRF-RACE-001"
        private val INITIAL_BALANCE = BigDecimal("1000.00")
        private val TRANSFER_AMOUNT = BigDecimal("100.00")
        private const val TOTAL_REQUESTS = 20
    }

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var productRepository: ProductRepository

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var transactionRepository: TransactionRepository

    @BeforeEach
    fun setUp() {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()

        if (productRepository.findByCode(PRODUCT_CODE) == null) {
            productRepository.save(
                Product(
                    code = PRODUCT_CODE,
                    name = "Transfer Race Product"
                )
            )
        }
    }

    @Test
    fun `동일 계좌쌍 동시 송금에서도 총합은 보존되고 음수 잔액이 발생하지 않는다`() {
        val fromAccount = createAccountWithBalance(customerId = System.nanoTime(), amount = INITIAL_BALANCE)
        val toAccount = createAccountWithBalance(customerId = System.nanoTime() + 1, amount = BigDecimal.ZERO.setScale(2))

        val requestBody = objectMapper.writeValueAsString(
            TransferRequest(
                fromAccountId = fromAccount.id!!,
                toAccountId = toAccount.id!!,
                amount = TRANSFER_AMOUNT
            )
        )

        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(TOTAL_REQUESTS)
        val exceptions = ConcurrentLinkedQueue<Throwable>()
        val executor = Executors.newFixedThreadPool(TOTAL_REQUESTS)

        val futures: List<Future<Int>> = (0 until TOTAL_REQUESTS).map {
            executor.submit(Callable {
                startLatch.await()
                try {
                    val result = mockMvc.post("/api/transfers") {
                        contentType = MediaType.APPLICATION_JSON
                        content = requestBody
                    }.andReturn()
                    result.response.status
                } catch (t: Throwable) {
                    exceptions.add(t)
                    500
                } finally {
                    doneLatch.countDown()
                }
            })
        }

        startLatch.countDown()
        doneLatch.await()
        executor.shutdown()

        val statuses = futures.map { it.get() }
        val successCount = statuses.count { it == 200 }
        val conflictCount = statuses.count { it == 409 }

        val latestFrom = accountRepository.findById(fromAccount.id!!).orElseThrow()
        val latestTo = accountRepository.findById(toAccount.id!!).orElseThrow()

        val totalBefore = fromAccount.balance + toAccount.balance
        val totalAfter = latestFrom.balance + latestTo.balance

        assertTrue(exceptions.isEmpty())
        assertEquals(TOTAL_REQUESTS, successCount + conflictCount)
        assertEquals(totalBefore, totalAfter)
        assertTrue(latestFrom.balance >= BigDecimal.ZERO.setScale(2))
        assertTrue(latestTo.balance >= BigDecimal.ZERO.setScale(2))

        val outTransactions = transactionRepository.findAllByAccountIdAndType(
            fromAccount.id!!,
            TransactionType.TRANSFER_OUT
        )
        val inTransactions = transactionRepository.findAllByAccountIdAndType(
            toAccount.id!!,
            TransactionType.TRANSFER_IN
        )

        assertEquals(successCount, outTransactions.size)
        assertEquals(successCount, inTransactions.size)
        assertFalse(successCount == 0)
    }

    private fun createAccountWithBalance(customerId: Long, amount: BigDecimal): Account {
        val createRequest = AccountCreateRequest(
            customerId = customerId,
            productCode = PRODUCT_CODE
        )

        val created = mockMvc.post("/api/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(createRequest)
        }.andExpect {
            status { isCreated() }
        }.andReturn()

        val accountResponse = objectMapper.readValue(created.response.contentAsString, AccountResponse::class.java)

        if (amount > BigDecimal.ZERO) {
            mockMvc.post("/api/accounts/${accountResponse.id}/deposit") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(AccountBalanceChangeRequest(amount))
            }.andExpect {
                status { isOk() }
            }
        }

        return accountRepository.findById(accountResponse.id).orElseThrow()
    }
}
