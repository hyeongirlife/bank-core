package com.bankcore.interest.repository

import com.bankcore.account.entity.Account
import com.bankcore.account.repository.AccountRepository
import com.bankcore.interest.entity.InterestLog
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.dao.DataIntegrityViolationException
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

@ActiveProfiles("local")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InterestLogRepositoryTest {

    @Autowired
    lateinit var interestLogRepository: InterestLogRepository

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    @Test
    fun `계좌와 영업일 기준으로 이자 로그 존재 여부를 조회한다`() {
        val product = productRepository.save(
            Product(code = "S${System.nanoTime().toString().takeLast(10)}", name = "Basic Savings")
        )
        val account = accountRepository.save(
            Account(
                customerId = 100L,
                accountNumber = "110-${System.nanoTime().toString().takeLast(15)}",
                product = product
            )
        )
        val businessDate = LocalDate.of(2026, 2, 20)

        interestLogRepository.save(
            InterestLog(
                account = account,
                businessDate = businessDate,
                baseRate = BigDecimal("0.0300"),
                spreadRate = BigDecimal("0.0010"),
                preferentialRate = BigDecimal("0.0020"),
                appliedRate = BigDecimal("0.0330"),
                balanceSnapshot = BigDecimal("1000000.00"),
                interestAmount = BigDecimal("90.00")
            )
        )

        val accountId = account.id!!
        val exists = interestLogRepository.existsByAccountIdAndBusinessDate(accountId, businessDate)
        val found = interestLogRepository.findByAccountIdAndBusinessDate(accountId, businessDate)

        assertTrue(exists)
        assertEquals(BigDecimal("90.00"), found?.interestAmount)
    }

    @Test
    fun `동일 계좌 동일 영업일 이자 로그 중복 저장 시 예외가 발생한다`() {
        val product = productRepository.save(
            Product(code = "S${System.nanoTime().toString().takeLast(10)}", name = "Flex Savings")
        )
        val account = accountRepository.save(
            Account(
                customerId = 101L,
                accountNumber = "110-${System.nanoTime().toString().takeLast(15)}",
                product = product
            )
        )
        val businessDate = LocalDate.of(2026, 2, 20)

        interestLogRepository.saveAndFlush(
            InterestLog(
                account = account,
                businessDate = businessDate,
                baseRate = BigDecimal("0.0300"),
                spreadRate = BigDecimal("0.0010"),
                preferentialRate = BigDecimal("0.0020"),
                appliedRate = BigDecimal("0.0330"),
                balanceSnapshot = BigDecimal("500000.00"),
                interestAmount = BigDecimal("45.00")
            )
        )

        assertThrows<DataIntegrityViolationException> {
            interestLogRepository.saveAndFlush(
                InterestLog(
                    account = account,
                    businessDate = businessDate,
                    baseRate = BigDecimal("0.0300"),
                    spreadRate = BigDecimal("0.0010"),
                    preferentialRate = BigDecimal("0.0020"),
                    appliedRate = BigDecimal("0.0330"),
                    balanceSnapshot = BigDecimal("500000.00"),
                    interestAmount = BigDecimal("45.00")
                )
            )
        }
    }
}
