package com.bankcore.transfer.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "송금 응답")
data class TransferResponse(
    @field:Schema(description = "출금 계좌 ID", example = "1")
    val fromAccountId: Long,

    @field:Schema(description = "입금 계좌 ID", example = "2")
    val toAccountId: Long,

    @field:Schema(description = "송금 금액", example = "10000.00")
    val amount: BigDecimal,

    @field:Schema(description = "출금 계좌 송금 후 잔액", example = "90000.00")
    val fromBalance: BigDecimal,

    @field:Schema(description = "입금 계좌 송금 후 잔액", example = "110000.00")
    val toBalance: BigDecimal,

    @field:Schema(description = "송금 시각", example = "2026-02-20T10:00:00")
    val transferredAt: LocalDateTime
)
