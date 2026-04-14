package com.bankcore.account.service

import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AccountBootstrapService(
    private val accountRepository: AccountRepository,
    private val productRepository: ProductRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
    private val distributedLockService: DistributedLockService,
    @Value("\${account.bootstrap.product-codes:SAV001}")
    bootstrapProductCodes: String
) {
    private val initialProductCodes = bootstrapProductCodes
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    @Transactional
    fun upsertInitialAccounts(customerId: Long): List<AccountResponse> {
        require(customerId >= 1L) { "고객 ID는 1 이상이어야 합니다" }

        if (initialProductCodes.isEmpty()) {
            throw IllegalStateException("초기화 상품 코드가 설정되지 않았습니다")
        }

        return distributedLockService.executeWithLock("account-bootstrap", "$customerId") {
            initialProductCodes.map { productCode ->
                val existing = accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                    customerId,
                    productCode,
                    AccountStatus.ACTIVE
                )

                if (existing != null) {
                    AccountResponse.from(existing)
                } else {
                    val product = productRepository.findByCode(productCode)
                        ?: throw NoSuchElementException("초기화 상품을 찾을 수 없습니다: $productCode")

                    val createdOrExisting = createOrFindActiveAccount(customerId, productCode, product)
                    AccountResponse.from(createdOrExisting)
                }
            }
        }
    }

    private fun createOrFindActiveAccount(customerId: Long, productCode: String, product: Product): Account {
        val now = LocalDateTime.now()
        return try {
            accountRepository.save(
                Account(
                    customerId = customerId,
                    accountNumber = accountNumberGenerator.generate(),
                    product = product,
                    openedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } catch (e: DataIntegrityViolationException) {
            accountRepository.findFirstByCustomerIdAndProductCodeAndStatusOrderByIdAsc(
                customerId,
                productCode,
                AccountStatus.ACTIVE
            ) ?: throw e
        }
    }
}
