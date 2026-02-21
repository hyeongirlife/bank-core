package com.bankcore.interest.repository

import com.bankcore.interest.entity.InterestLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate

interface InterestLogRepository : JpaRepository<InterestLog, Long> {
    fun existsByAccountIdAndBusinessDate(accountId: Long, businessDate: LocalDate): Boolean
    fun findByAccountIdAndBusinessDate(accountId: Long, businessDate: LocalDate): InterestLog?

    @Query(
        """
        select coalesce(sum(i.interestAmount), 0)
        from InterestLog i
        where i.account.id = :accountId
          and i.businessDate between :fromBusinessDate and :toBusinessDate
        """
    )
    fun sumInterestAmountByAccountIdAndBusinessDateBetween(
        @Param("accountId") accountId: Long,
        @Param("fromBusinessDate") fromBusinessDate: LocalDate,
        @Param("toBusinessDate") toBusinessDate: LocalDate
    ): BigDecimal
}
