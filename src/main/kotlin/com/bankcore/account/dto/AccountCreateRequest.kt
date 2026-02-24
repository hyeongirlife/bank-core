package com.bankcore.account.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.time.LocalDate

@Schema(description = "계좌 개설 요청")
data class AccountCreateRequest(
    @field:Schema(description = "고객 ID", example = "1")
    @field:NotNull(message = "고객 ID는 필수입니다")
    @field:Positive(message = "고객 ID는 1 이상이어야 합니다")
    val customerId: Long?,

    @field:Schema(description = "상품 코드", example = "SAV001")
    @field:NotBlank(message = "상품 코드는 필수입니다")
    @field:Pattern(
        regexp = "^[A-Z0-9]{3,20}$",
        message = "상품 코드는 대문자/숫자 3~20자리여야 합니다"
    )
    val productCode: String,

    @field:Schema(description = "만기일(선택)", example = "2026-12-31", nullable = true)
    val maturityDate: LocalDate? = null
)
