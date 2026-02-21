package com.bankcore.account.repository

import com.bankcore.account.entity.Account
import com.bankcore.account.entity.AccountStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccountRepository : JpaRepository<Account, Long> {
    fun findByAccountNumber(accountNumber: String): Account?
    fun findByCustomerId(customerId: Long): List<Account>
    fun countByCustomerIdAndProductCodeAndStatus(customerId: Long, productCode: String, status: AccountStatus): Long
    fun existsByAccountNumber(accountNumber: String): Boolean
    fun findAllByStatus(status: AccountStatus, pageable: Pageable): Page<Account>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Account?
}
