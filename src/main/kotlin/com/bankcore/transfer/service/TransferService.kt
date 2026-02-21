package com.bankcore.transfer.service

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.transaction.entity.Transaction
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.bankcore.transaction.service.TransactionNumberGenerator
import com.bankcore.transfer.dto.TransferRequest
import com.bankcore.transfer.dto.TransferResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class TransferService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionNumberGenerator: TransactionNumberGenerator,
    private val distributedLockService: DistributedLockService,
    @Value("\${transfer.lock.strategy:DISTRIBUTED}")
    lockStrategy: String
) {
    private val transferLockStrategy: TransferLockStrategy = TransferLockStrategy.from(lockStrategy)

    @Transactional
    fun transfer(request: TransferRequest): TransferResponse {
        validateSameAccount(request)

        val fromId = request.fromAccountId
        val toId = request.toAccountId
        val minId = minOf(fromId, toId)
        val maxId = maxOf(fromId, toId)

        return when (transferLockStrategy) {
            TransferLockStrategy.DISTRIBUTED -> distributedLockService.executeWithLock("account-transfer", "$minId:$maxId") {
                executeTransfer(
                    fromAccount = findFromAccount(fromId),
                    toAccount = findToAccount(toId),
                    request = request
                )
            }

            TransferLockStrategy.PESSIMISTIC -> {
                val (fromAccount, toAccount) = findLockedAccounts(fromId, toId)
                executeTransfer(
                    fromAccount = fromAccount,
                    toAccount = toAccount,
                    request = request
                )
            }
        }
    }

    private fun findFromAccount(fromId: Long): Account {
        return accountRepository.findById(fromId)
            .orElseThrow { NoSuchElementException("출금 계좌를 찾을 수 없습니다: $fromId") }
    }

    private fun findToAccount(toId: Long): Account {
        return accountRepository.findById(toId)
            .orElseThrow { NoSuchElementException("입금 계좌를 찾을 수 없습니다: $toId") }
    }

    private fun findLockedAccounts(fromId: Long, toId: Long): Pair<Account, Account> {
        val minId = minOf(fromId, toId)
        val maxId = maxOf(fromId, toId)

        val lockedMinAccount = findLockedAccount(minId, fromId, toId)
        val lockedMaxAccount = findLockedAccount(maxId, fromId, toId)

        return if (fromId == minId) {
            Pair(lockedMinAccount, lockedMaxAccount)
        } else {
            Pair(lockedMaxAccount, lockedMinAccount)
        }
    }

    private fun findLockedAccount(id: Long, fromId: Long, toId: Long): Account {
        return accountRepository.findByIdForUpdate(id)
            ?: when (id) {
                fromId -> throw NoSuchElementException("출금 계좌를 찾을 수 없습니다: $fromId")
                toId -> throw NoSuchElementException("입금 계좌를 찾을 수 없습니다: $toId")
                else -> throw NoSuchElementException("계좌를 찾을 수 없습니다: $id")
            }
    }

    private fun executeTransfer(
        fromAccount: Account,
        toAccount: Account,
        request: TransferRequest
    ): TransferResponse {
        if (fromAccount.status == AccountStatus.CLOSED) {
            throw IllegalStateException("출금 계좌가 해지 상태입니다")
        }
        if (toAccount.status == AccountStatus.CLOSED) {
            throw IllegalStateException("입금 계좌가 해지 상태입니다")
        }
        if (fromAccount.balance < request.amount) {
            throw IllegalStateException("잔액이 부족합니다")
        }

        val now = LocalDateTime.now()
        val updatedFrom = accountRepository.save(
            fromAccount.copy(
                balance = fromAccount.balance - request.amount,
                updatedAt = now
            )
        )
        val updatedTo = accountRepository.save(
            toAccount.copy(
                balance = toAccount.balance + request.amount,
                updatedAt = now
            )
        )

        transactionRepository.save(
            Transaction(
                transactionNumber = transactionNumberGenerator.generate(),
                account = updatedFrom,
                type = TransactionType.TRANSFER_OUT,
                amount = request.amount,
                balanceAfter = updatedFrom.balance,
                transactionAt = now
            )
        )
        transactionRepository.save(
            Transaction(
                transactionNumber = transactionNumberGenerator.generate(),
                account = updatedTo,
                type = TransactionType.TRANSFER_IN,
                amount = request.amount,
                balanceAfter = updatedTo.balance,
                transactionAt = now
            )
        )

        return TransferResponse(
            fromAccountId = updatedFrom.id!!,
            toAccountId = updatedTo.id!!,
            amount = request.amount,
            fromBalance = updatedFrom.balance,
            toBalance = updatedTo.balance,
            transferredAt = now
        )
    }

    private fun validateSameAccount(request: TransferRequest) {
        if (request.fromAccountId == request.toAccountId) {
            throw IllegalArgumentException("동일 계좌로는 송금할 수 없습니다")
        }
    }
}
