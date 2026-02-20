package com.bankcore.rate.repository

import com.bankcore.rate.entity.SpreadRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SpreadRateRepository : JpaRepository<SpreadRate, Long> {
    fun findByProductCodeAndBusinessDateAndIsActiveTrue(productCode: String, businessDate: LocalDate): SpreadRate?
}
