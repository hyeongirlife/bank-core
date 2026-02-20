package com.bankcore.interest.service

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import com.bankcore.interest.dto.DailyInterestAccrualResponse
import com.bankcore.interest.entity.InterestLog
import com.bankcore.interest.repository.InterestLogRepository
import com.bankcore.rate.repository.BaseRateRepository
import com.bankcore.rate.repository.PreferentialRateRepository
import com.bankcore.rate.repository.SpreadRateRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class DailyInterestAccrualService(
    private val accountRepository: AccountRepository,
    private val baseRateRepository: BaseRateRepository,
    private val spreadRateRepository: SpreadRateRepository,
    private val preferentialRateRepository: PreferentialRateRepository,
    private val interestLogRepository: InterestLogRepository,
    private val distributedLockService: DistributedLockService,
    private val interestAmountCalculator: InterestAmountCalculator
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun accrueDailyInterest(accountId: Long, request: DailyInterestAccrualRequest): DailyInterestAccrualResponse {
        val businessDate = request.businessDate

        val existingBeforeLock = interestLogRepository.findByAccountIdAndBusinessDate(accountId, businessDate)
        if (existingBeforeLock != null) {
            return toResponse(existingBeforeLock, true)
        }

        return distributedLockService.executeWithLock("interest-accrual", "$accountId:$businessDate") {
            val existingAfterLock = interestLogRepository.findByAccountIdAndBusinessDate(accountId, businessDate)
            if (existingAfterLock != null) {
                return@executeWithLock toResponse(existingAfterLock, true)
            }

            val account = accountRepository.findById(accountId)
                .orElseThrow { NoSuchElementException("계좌를 찾을 수 없습니다: $accountId") }

            validateAccountLifecycle(account, businessDate)

            val baseRate = baseRateRepository.findByBusinessDate(businessDate)
                ?: throw IllegalStateException("기준금리가 설정되지 않았습니다: $businessDate")

            val spreadRate = spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue(account.product.code, businessDate)
                ?: throw IllegalStateException("가산금리가 설정되지 않았습니다: ${account.product.code}, $businessDate")

            val normalizedConditionCodes = request.conditionCodes.map { it.trim() }
            val preferentialSum = if (normalizedConditionCodes.isEmpty()) {
                BigDecimal.ZERO
            } else {
                preferentialRateRepository
                    .findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
                        account.product.code,
                        normalizedConditionCodes,
                        businessDate
                    )
                    .fold(BigDecimal.ZERO) { acc, rate -> acc.add(rate.rate) }
            }

            val appliedRate = interestAmountCalculator.calculateAppliedRate(baseRate.rate, spreadRate.rate, preferentialSum)
            val interestAmount = interestAmountCalculator.calculateDailyInterest(account.balance, appliedRate)

            val log = InterestLog(
                account = account,
                businessDate = businessDate,
                baseRate = baseRate.rate,
                spreadRate = spreadRate.rate,
                preferentialRate = preferentialSum,
                appliedRate = appliedRate,
                balanceSnapshot = account.balance,
                interestAmount = interestAmount
            )

            try {
                val saved = interestLogRepository.saveAndFlush(log)
                toResponse(saved, false)
            } catch (e: DataIntegrityViolationException) {
                val existingAfterConflict = interestLogRepository.findByAccountIdAndBusinessDate(accountId, businessDate)
                    ?: throw e
                toResponse(existingAfterConflict, true)
            }
        }
    }

    private fun validateAccountLifecycle(account: Account, businessDate: LocalDate) {
        if (account.status == AccountStatus.CLOSED) {
            throw IllegalStateException("해지된 계좌에는 일별 이자를 적립할 수 없습니다")
        }

        val openedDate = account.openedAt.toLocalDate()
        if (businessDate.isBefore(openedDate)) {
            throw IllegalStateException("영업일이 계좌 개설일 이전입니다: $businessDate")
        }

        val closedDate = account.closedAt?.toLocalDate()
        if (closedDate != null && businessDate.isAfter(closedDate)) {
            throw IllegalStateException("영업일이 계좌 해지일 이후입니다: $businessDate")
        }
    }

    private fun toResponse(log: InterestLog, alreadyProcessed: Boolean): DailyInterestAccrualResponse {
        return DailyInterestAccrualResponse(
            accountId = log.account.id!!,
            businessDate = log.businessDate,
            baseRate = log.baseRate,
            spreadRate = log.spreadRate,
            preferentialRate = log.preferentialRate,
            appliedRate = log.appliedRate,
            balanceSnapshot = log.balanceSnapshot,
            interestAmount = log.interestAmount,
            alreadyProcessed = alreadyProcessed
        )
    }
}
