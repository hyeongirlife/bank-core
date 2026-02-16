package com.bankcore.account.dto

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class AccountResponse(
    val id: Long,
    val customerId: Long,
    val accountNumber: String,
    val productCode: String,
    val productName: String,
    val balance: BigDecimal,
    val status: AccountStatus,
    val openedAt: LocalDateTime,
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
