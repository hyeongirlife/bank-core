package com.bankcore.interest.repository

import com.bankcore.interest.entity.InterestLog
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface InterestLogRepository : JpaRepository<InterestLog, Long> {
    fun existsByAccountIdAndBusinessDate(accountId: Long, businessDate: LocalDate): Boolean
    fun findByAccountIdAndBusinessDate(accountId: Long, businessDate: LocalDate): InterestLog?
}
