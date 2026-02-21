package com.bankcore.interest.service

import com.bankcore.account.entity.Account
import com.bankcore.common.lock.DistributedLockService
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import com.bankcore.interest.entity.InterestSettlement
import com.bankcore.interest.entity.InterestSettlementType
import com.bankcore.interest.repository.InterestLogRepository
import com.bankcore.interest.repository.InterestSettlementRepository
import com.bankcore.transaction.entity.Transaction
import com.bankcore.transaction.entity.TransactionType
import com.bankcore.transaction.repository.TransactionRepository
import com.bankcore.transaction.service.TransactionNumberGenerator
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class EarlyTerminationSettlementService(
    private val dailyInterestAccrualService: DailyInterestAccrualService,
    private val interestLogRepository: InterestLogRepository,
    private val interestSettlementRepository: InterestSettlementRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionNumberGenerator: TransactionNumberGenerator,
    private val distributedLockService: DistributedLockService,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul"))
) {
    companion object {
        private const val MONEY_SCALE = 2
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun settleOnClose(account: Account, closeBusinessDate: LocalDate): EarlyTerminationSettlementResult {
        val accountId = account.id ?: throw IllegalArgumentException("계좌 ID가 없습니다")
        val maturityDate = account.maturityDate

        if (maturityDate == null || !closeBusinessDate.isBefore(maturityDate)) {
            return EarlyTerminationSettlementResult.skipped(closeBusinessDate)
        }

        return distributedLockService.executeWithLock(
            "interest-settlement",
            "$accountId:${InterestSettlementType.EARLY_TERMINATION}"
        ) {
            val existingBeforeLock = interestSettlementRepository
                .findByAccountIdAndSettlementType(accountId, InterestSettlementType.EARLY_TERMINATION)
            if (existingBeforeLock != null) {
                return@executeWithLock EarlyTerminationSettlementResult.alreadyProcessed(existingBeforeLock)
            }

            dailyInterestAccrualService.accrueDailyInterest(
                accountId,
                DailyInterestAccrualRequest(
                    businessDate = closeBusinessDate,
                    conditionCodes = emptyList()
                )
            )

            val cumulativeInterest = interestLogRepository
                .sumInterestAmountByAccountIdAndBusinessDateBetween(
                    accountId,
                    account.openedAt.toLocalDate(),
                    closeBusinessDate
                )
                .setScale(MONEY_SCALE, RoundingMode.DOWN)

            val settlement = InterestSettlement(
                account = account,
                settlementType = InterestSettlementType.EARLY_TERMINATION,
                businessDate = closeBusinessDate,
                interestAmount = cumulativeInterest
            )

            val persistedSettlement = try {
                interestSettlementRepository.saveAndFlush(settlement)
            } catch (e: DataIntegrityViolationException) {
                interestSettlementRepository
                    .findByAccountIdAndSettlementType(accountId, InterestSettlementType.EARLY_TERMINATION)
                    ?: throw e
            }

            val alreadyProcessed = persistedSettlement.id != settlement.id
            if (alreadyProcessed) {
                return@executeWithLock EarlyTerminationSettlementResult.alreadyProcessed(persistedSettlement)
            }

            transactionRepository.save(
                Transaction(
                    transactionNumber = transactionNumberGenerator.generate(),
                    account = account,
                    type = TransactionType.INTEREST_SETTLEMENT,
                    amount = persistedSettlement.interestAmount,
                    balanceAfter = account.balance,
                    transactionAt = LocalDateTime.now(clock)
                )
            )

            EarlyTerminationSettlementResult.settled(persistedSettlement)
        }
    }
}

data class EarlyTerminationSettlementResult(
    val skipped: Boolean,
    val settled: Boolean,
    val alreadyProcessed: Boolean,
    val settlementType: InterestSettlementType,
    val businessDate: LocalDate,
    val interestAmount: BigDecimal
) {
    companion object {
        fun skipped(businessDate: LocalDate): EarlyTerminationSettlementResult {
            return EarlyTerminationSettlementResult(
                skipped = true,
                settled = false,
                alreadyProcessed = false,
                settlementType = InterestSettlementType.EARLY_TERMINATION,
                businessDate = businessDate,
                interestAmount = BigDecimal.ZERO.setScale(2, RoundingMode.DOWN)
            )
        }

        fun settled(settlement: InterestSettlement): EarlyTerminationSettlementResult {
            return EarlyTerminationSettlementResult(
                skipped = false,
                settled = true,
                alreadyProcessed = false,
                settlementType = settlement.settlementType,
                businessDate = settlement.businessDate,
                interestAmount = settlement.interestAmount
            )
        }

        fun alreadyProcessed(settlement: InterestSettlement): EarlyTerminationSettlementResult {
            return EarlyTerminationSettlementResult(
                skipped = false,
                settled = true,
                alreadyProcessed = true,
                settlementType = settlement.settlementType,
                businessDate = settlement.businessDate,
                interestAmount = settlement.interestAmount
            )
        }
    }
}
