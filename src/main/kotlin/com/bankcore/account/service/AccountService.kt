package com.bankcore.account.service

import com.bankcore.account.dto.AccountBalanceChangeRequest
import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.product.repository.ProductRepository
import com.bankcore.transaction.entity.Transaction
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.bankcore.transaction.service.TransactionNumberGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val productRepository: ProductRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
    private val distributedLockService: DistributedLockService,
    private val transactionRepository: TransactionRepository,
    private val transactionNumberGenerator: TransactionNumberGenerator
) {
    @Transactional
    fun createAccount(request: AccountCreateRequest): AccountResponse {
        return distributedLockService.executeWithLock("account", "${request.customerId}:${request.productCode}") {
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

            AccountResponse.from(accountRepository.save(account))
        }
    }

    @Transactional(readOnly = true)
    fun getAccount(id: Long): AccountResponse {
        val account = accountRepository.findById(id)
            .orElseThrow { NoSuchElementException("계좌를 찾을 수 없습니다: $id") }

        return AccountResponse.from(account)
    }

    @Transactional
    fun closeAccount(id: Long): AccountResponse {
        return distributedLockService.executeWithLock("account", "$id") {
            val account = accountRepository.findById(id)
                .orElseThrow { NoSuchElementException("계좌를 찾을 수 없습니다: $id") }

            if (account.status == AccountStatus.CLOSED) {
                throw IllegalStateException("이미 해지된 계좌입니다")
            }

            if (account.balance.compareTo(BigDecimal.ZERO) != 0) {
                throw IllegalStateException("잔액이 남아있는 계좌는 해지할 수 없습니다")
            }

            val now = LocalDateTime.now()
            val closedAccount = account.copy(
                status = AccountStatus.CLOSED,
                closedAt = now,
                updatedAt = now
            )

            AccountResponse.from(accountRepository.save(closedAccount))
        }
    }

    @Transactional
    fun deposit(id: Long, request: AccountBalanceChangeRequest): AccountResponse {
        return distributedLockService.executeWithLock("account", "$id") {
            val account = accountRepository.findById(id)
                .orElseThrow { NoSuchElementException("계좌를 찾을 수 없습니다: $id") }

            if (account.status == AccountStatus.CLOSED) {
                throw IllegalStateException("해지된 계좌에는 입금할 수 없습니다")
            }

            val now = LocalDateTime.now()
            val updatedAccount = accountRepository.save(
                account.copy(
                    balance = account.balance + request.amount,
                    updatedAt = now
                )
            )

            transactionRepository.save(
                Transaction(
                    transactionNumber = transactionNumberGenerator.generate(),
                    account = updatedAccount,
                    type = TransactionType.DEPOSIT,
                    amount = request.amount,
                    balanceAfter = updatedAccount.balance,
                    transactionAt = now
                )
            )

            AccountResponse.from(updatedAccount)
        }
    }

    @Transactional
    fun withdraw(id: Long, request: AccountBalanceChangeRequest): AccountResponse {
        return distributedLockService.executeWithLock("account", "$id") {
            val account = accountRepository.findById(id)
                .orElseThrow { NoSuchElementException("계좌를 찾을 수 없습니다: $id") }

            if (account.status == AccountStatus.CLOSED) {
                throw IllegalStateException("해지된 계좌에서는 출금할 수 없습니다")
            }

            if (account.balance < request.amount) {
                throw IllegalStateException("잔액이 부족합니다")
            }

            val now = LocalDateTime.now()
            val updatedAccount = accountRepository.save(
                account.copy(
                    balance = account.balance - request.amount,
                    updatedAt = now
                )
            )

            transactionRepository.save(
                Transaction(
                    transactionNumber = transactionNumberGenerator.generate(),
                    account = updatedAccount,
                    type = TransactionType.WITHDRAWAL,
                    amount = request.amount,
                    balanceAfter = updatedAccount.balance,
                    transactionAt = now
                )
            )

            AccountResponse.from(updatedAccount)
        }
    }
}
