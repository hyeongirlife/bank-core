package com.bankcore.account.service

import com.bankcore.account.repository.AccountRepository
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class AccountNumberGenerator(
    private val accountRepository: AccountRepository
) {
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 10
    }

    fun generate(): String {
        repeat(MAX_RETRY_ATTEMPTS) {
            val accountNumber = generateRandom()
            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber
            }
        }
        throw IllegalStateException("계좌번호 생성에 실패했습니다. 최대 재시도 횟수를 초과했습니다.")
    }

    protected open fun generateRandom(): String {
        val mid = Random.nextInt(100, 1000)
        val last = Random.nextInt(100000, 1000000)
        return "110-%03d-%06d".format(mid, last)
    }
}
