package com.bankcore.rate.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.PastOrPresent
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "기준금리 등록 요청")
data class BaseRateUpsertRequest(
    @field:Schema(description = "영업일", example = "2026-02-20")
    @field:PastOrPresent(message = "영업일은 미래일 수 없습니다")
    val businessDate: LocalDate,

    @field:Schema(description = "기준금리", example = "0.0300")
    @field:DecimalMin(value = "0.0000", inclusive = false, message = "금리는 0보다 커야 합니다")
    @field:Digits(integer = 1, fraction = 4)
    val rate: BigDecimal
)
