package com.bankcore.rate.repository

import com.bankcore.rate.entity.PreferentialRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface PreferentialRateRepository : JpaRepository<PreferentialRate, Long> {
    fun findAllByProductCodeAndBusinessDateAndIsActiveTrue(productCode: String, businessDate: LocalDate): List<PreferentialRate>
    fun findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
        productCode: String,
        conditionCodes: List<String>,
        businessDate: LocalDate
    ): List<PreferentialRate>
}
