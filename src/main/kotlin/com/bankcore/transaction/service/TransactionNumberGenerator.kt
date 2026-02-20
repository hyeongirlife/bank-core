package com.bankcore.transaction.service

import com.bankcore.transaction.repository.TransactionRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

@Component
class TransactionNumberGenerator(
    private val transactionRepository: TransactionRepository
) {
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 10
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }

    fun generate(): String {
        repeat(MAX_RETRY_ATTEMPTS) {
            val transactionNumber = generateRandom()
            if (!transactionRepository.existsByTransactionNumber(transactionNumber)) {
                return transactionNumber
            }
        }

        throw IllegalStateException("거래번호 생성에 실패했습니다. 최대 재시도 횟수를 초과했습니다.")
    }

    protected open fun generateRandom(): String {
        val timestamp = LocalDateTime.now().format(FORMATTER)
        val suffix = Random.nextInt(1000, 10000)
        return "TXN-$timestamp-$suffix"
    }
}
