package com.bankcore.account.repository

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository : JpaRepository<Account, Long> {
    fun findByAccountNumber(accountNumber: String): Account?
    fun findByCustomerId(customerId: Long): List<Account>
    fun countByCustomerIdAndProductCodeAndStatus(customerId: Long, productCode: String, status: AccountStatus): Long
    fun existsByAccountNumber(accountNumber: String): Boolean
    fun findAllByStatus(status: AccountStatus, pageable: Pageable): Page<Account>
}
