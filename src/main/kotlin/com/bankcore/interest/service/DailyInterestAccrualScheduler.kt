package com.bankcore.interest.service

import com.bankcore.account.entity.AccountStatus
import com.bankcore.account.repository.AccountRepository
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@Component
class DailyInterestAccrualScheduler(
    private val accountRepository: AccountRepository,
    private val dailyInterestAccrualService: DailyInterestAccrualService,
    @Value("\${interest.batch.run-once:false}")
    private val runOnce: Boolean,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul"))
) {
    companion object {
        private const val PAGE_SIZE = 100
        private val PAGE_SORT: Sort = Sort.by(Sort.Direction.ASC, "id")
        private val log = LoggerFactory.getLogger(DailyInterestAccrualScheduler::class.java)
    }

    @Scheduled(
        cron = "${'$'}{interest.scheduler.daily-accrual-cron:0 10 0 * * *}",
        zone = "Asia/Seoul"
    )
    fun runDailyAccrual() {
        if (runOnce) {
            log.info("run-once 모드에서는 스케줄 실행을 건너뜁니다")
            return
        }
        executeAccrual()
    }

    fun runDailyAccrualOnce() {
        executeAccrual()
    }

    private fun executeAccrual() {
        val businessDate = LocalDate.now(clock)
        var page = 0
        var failureCount = 0

        while (true) {
            val accounts = accountRepository.findAllByStatus(
                AccountStatus.ACTIVE,
                PageRequest.of(page, PAGE_SIZE, PAGE_SORT)
            )
            if (accounts.content.isEmpty()) {
                break
            }

            accounts.content.forEach { account ->
                try {
                    dailyInterestAccrualService.accrueDailyInterest(
                        account.id!!,
                        DailyInterestAccrualRequest(
                            businessDate = businessDate,
                            conditionCodes = emptyList()
                        )
                    )
                } catch (e: RuntimeException) {
                    failureCount += 1
                    log.warn("일별 이자 적립 실패 - accountId={}, businessDate={}", account.id, businessDate, e)
                }
            }

            if (!accounts.hasNext()) {
                break
            }
            page += 1
        }

        if (failureCount > 0) {
            throw IllegalStateException("일별 이자 적립 중 실패가 발생했습니다. failureCount=$failureCount")
        }
    }
}
