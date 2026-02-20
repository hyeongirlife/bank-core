package com.bankcore.interest.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "일별 이자 적립 응답")
data class DailyInterestAccrualResponse(
    @field:Schema(description = "계좌 ID", example = "1")
    val accountId: Long,

    @field:Schema(description = "영업일", example = "2026-02-20")
    val businessDate: LocalDate,

    @field:Schema(description = "기준금리", example = "0.0300")
    val baseRate: BigDecimal,

    @field:Schema(description = "가산금리", example = "0.0010")
    val spreadRate: BigDecimal,

    @field:Schema(description = "우대금리 합계", example = "0.0020")
    val preferentialRate: BigDecimal,

    @field:Schema(description = "적용금리", example = "0.0330")
    val appliedRate: BigDecimal,

    @field:Schema(description = "이자 계산 기준 잔액", example = "1000000.00")
    val balanceSnapshot: BigDecimal,

    @field:Schema(description = "계산된 일 이자", example = "90.41")
    val interestAmount: BigDecimal,

    @field:Schema(description = "동일 영업일 기존 처리 여부", example = "false")
    val alreadyProcessed: Boolean
)
