package com.bankcore.rate.repository

import com.bankcore.rate.entity.BaseRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface BaseRateRepository : JpaRepository<BaseRate, Long> {
    fun findByBusinessDate(businessDate: LocalDate): BaseRate?
}
