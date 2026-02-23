package com.bankcore.interest.repository

import com.bankcore.interest.entity.InterestSettlement
import com.bankcore.interest.entity.InterestSettlementType
import org.springframework.data.jpa.repository.JpaRepository

interface InterestSettlementRepository : JpaRepository<InterestSettlement, Long> {
    fun findByAccountIdAndSettlementType(accountId: Long, settlementType: InterestSettlementType): InterestSettlement?
    fun findAllByAccountIdAndSettlementType(accountId: Long, settlementType: InterestSettlementType): List<InterestSettlement>
}
