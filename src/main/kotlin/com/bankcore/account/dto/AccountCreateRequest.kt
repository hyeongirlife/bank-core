package com.bankcore.account.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class AccountCreateRequest(
    @field:Positive
    val customerId: Long,
    @field:NotBlank
    val productCode: String
)
