package com.bankcore.rate.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PastOrPresent
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "가산금리 등록 요청")
data class SpreadRateUpsertRequest(
    @field:Schema(description = "상품 코드", example = "SAV001")
    @field:NotBlank(message = "상품 코드는 필수입니다")
    val productCode: String,

    @field:Schema(description = "영업일", example = "2026-02-20")
    @field:PastOrPresent(message = "영업일은 미래일 수 없습니다")
    val businessDate: LocalDate,

    @field:Schema(description = "가산금리", example = "0.0010")
    @field:DecimalMin(value = "0.0000", inclusive = false, message = "금리는 0보다 커야 합니다")
    @field:Digits(integer = 1, fraction = 4)
    val rate: BigDecimal
)
