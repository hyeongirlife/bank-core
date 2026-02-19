package com.bankcore.account.controller

import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.service.AccountService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService
) {
    @PostMapping
    fun createAccount(@Valid @RequestBody request: AccountCreateRequest): ResponseEntity<AccountResponse> {
        val response = accountService.createAccount(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{id}")
    fun getAccount(@PathVariable id: Long): ResponseEntity<AccountResponse> {
        val response = accountService.getAccount(id)
        return ResponseEntity.ok(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad Request")))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (e.message ?: "Conflict")))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "Not Found")))
    }
}
