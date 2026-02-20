package com.bankcore.account.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

@Schema(description = "계좌 개설 요청")
data class AccountCreateRequest(
    @field:Schema(description = "고객 ID", example = "1")
    @field:Positive
    val customerId: Long,

    @field:Schema(description = "상품 코드", example = "SAV001")
    @field:NotBlank
    val productCode: String
)
