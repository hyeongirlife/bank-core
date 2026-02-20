package com.bankcore.account.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

@Schema(description = "계좌 입출금 요청")
data class AccountBalanceChangeRequest(
    @field:Schema(description = "금액", example = "1000000.00")
    @field:Positive
    @field:Digits(integer = 16, fraction = 2)
    val amount: BigDecimal
)
