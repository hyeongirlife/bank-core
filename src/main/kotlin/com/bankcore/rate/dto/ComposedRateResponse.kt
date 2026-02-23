package com.bankcore.rate.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "복합금리 조회 응답")
data class ComposedRateResponse(
    @field:Schema(description = "상품 코드", example = "SAV001")
    val productCode: String,

    @field:Schema(description = "영업일", example = "2026-02-20")
    val businessDate: LocalDate,

    @field:Schema(description = "우대 조건 코드 목록", example = "[\"SALARY_TRANSFER\"]")
    val conditionCodes: List<String>,

    @field:Schema(description = "기준금리", example = "0.0300")
    val baseRate: BigDecimal,

    @field:Schema(description = "가산금리", example = "0.0010")
    val spreadRate: BigDecimal,

    @field:Schema(description = "우대금리 합계", example = "0.0020")
    val preferentialRate: BigDecimal,

    @field:Schema(description = "적용금리", example = "0.0330")
    val appliedRate: BigDecimal
)
