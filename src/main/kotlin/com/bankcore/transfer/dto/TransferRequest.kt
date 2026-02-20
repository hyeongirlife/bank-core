package com.bankcore.transfer.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

@Schema(description = "송금 요청")
data class TransferRequest(
    @field:Schema(description = "출금 계좌 ID", example = "1")
    @field:Positive
    val fromAccountId: Long,

    @field:Schema(description = "입금 계좌 ID", example = "2")
    @field:Positive
    val toAccountId: Long,

    @field:Schema(description = "송금 금액", example = "10000.00")
    @field:Positive
    @field:Digits(integer = 16, fraction = 2)
    val amount: BigDecimal
)
