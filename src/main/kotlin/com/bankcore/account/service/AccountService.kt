package com.bankcore.account.service

import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.product.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val productRepository: ProductRepository,
    private val accountNumberGenerator: AccountNumberGenerator
) {
    @Transactional
    fun createAccount(request: AccountCreateRequest): AccountResponse {
        val product = productRepository.findByCode(request.productCode)
            ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: ${request.productCode}")

        if (product.maxAccountPerCustomer > 0) {
            val activeCount = accountRepository.countByCustomerIdAndProductCodeAndStatus(
                request.customerId, request.productCode, AccountStatus.ACTIVE
            )
            if (activeCount >= product.maxAccountPerCustomer) {
                throw IllegalStateException("해당 상품의 최대 계좌 개설 수를 초과했습니다")
            }
        }

        val account = Account(
            customerId = request.customerId,
            accountNumber = accountNumberGenerator.generate(),
            product = product
        )

        return AccountResponse.from(accountRepository.save(account))
    }
}
