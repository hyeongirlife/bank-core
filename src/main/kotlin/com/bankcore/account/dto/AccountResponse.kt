package com.bankcore.account.dto

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "계좌 응답")
data class AccountResponse(
    @field:Schema(description = "계좌 ID", example = "1")
    val id: Long,

    @field:Schema(description = "고객 ID", example = "1")
    val customerId: Long,

    @field:Schema(description = "계좌번호", example = "110-123-456789")
    val accountNumber: String,

    @field:Schema(description = "상품 코드", example = "SAV001")
    val productCode: String,

    @field:Schema(description = "상품명", example = "Basic Savings")
    val productName: String,

    @field:Schema(description = "잔액", example = "0.00")
    val balance: BigDecimal,

    @field:Schema(description = "계좌 상태", example = "ACTIVE")
    val status: AccountStatus,

    @field:Schema(description = "개설일시", example = "2026-02-20T10:00:00")
    val openedAt: LocalDateTime,

    @field:Schema(description = "해지일시", example = "2026-02-20T11:00:00")
    val closedAt: LocalDateTime?
) {
    companion object {
        fun from(account: Account) = AccountResponse(
            id = account.id!!,
            customerId = account.customerId,
            accountNumber = account.accountNumber,
            productCode = account.product.code,
            productName = account.product.name,
            balance = account.balance,
            status = account.status,
            openedAt = account.openedAt,
            closedAt = account.closedAt
        )
    }
}
