package com.bankcore.rate.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "우대금리 등록 요청")
data class PreferentialRateUpsertRequest(
    @field:Schema(description = "상품 코드", example = "SAV001")
    @field:NotBlank(message = "상품 코드는 필수입니다")
    val productCode: String,

    @field:Schema(description = "우대 조건 코드", example = "SALARY_TRANSFER")
    @field:NotBlank(message = "조건 코드는 필수입니다")
    @field:Pattern(regexp = "^[A-Z_]{1,50}$", message = "조건 코드는 영문 대문자와 언더스코어만 허용됩니다")
    val conditionCode: String,

    @field:Schema(description = "영업일", example = "2026-02-20")
    @field:PastOrPresent(message = "영업일은 미래일 수 없습니다")
    val businessDate: LocalDate,

    @field:Schema(description = "우대금리", example = "0.0020")
    @field:DecimalMin(value = "0.0000", inclusive = false, message = "금리는 0보다 커야 합니다")
    @field:Digits(integer = 1, fraction = 4)
    val rate: BigDecimal
)
