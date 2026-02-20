package com.bankcore.account.controller

import com.bankcore.account.dto.AccountBalanceChangeRequest
import com.bankcore.account.dto.AccountCreateRequest
import com.bankcore.account.dto.AccountResponse
import com.bankcore.account.service.AccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*

@Schema(description = "공통 에러 응답")
data class ErrorResponse(
    @field:Schema(description = "에러 메시지", example = "요청이 처리 중입니다")
    val error: String
)

@Tag(name = "Accounts", description = "계좌 CRUD API")
@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService
) {
    @Operation(summary = "계좌 개설", description = "신규 계좌를 개설합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "계좌 개설 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "비즈니스 규칙 위반",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping
    fun createAccount(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "create-1-k1"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: AccountCreateRequest
    ): ResponseEntity<AccountResponse> {
        val response = accountService.createAccount(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "계좌 조회", description = "계좌 ID로 계좌 정보를 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "계좌 조회 성공"),
            ApiResponse(
                responseCode = "404",
                description = "계좌를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @GetMapping("/{id}")
    fun getAccount(
        @Parameter(description = "계좌 ID", example = "1")
        @PathVariable id: Long
    ): ResponseEntity<AccountResponse> {
        val response = accountService.getAccount(id)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "계좌 입금", description = "ACTIVE 계좌에 금액을 입금합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "입금 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "계좌를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "해지 계좌 입금 불가",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/{id}/deposit")
    fun deposit(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "deposit-1-k1"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Parameter(description = "계좌 ID", example = "1")
        @PathVariable id: Long,
        @Valid @RequestBody request: AccountBalanceChangeRequest
    ): ResponseEntity<AccountResponse> {
        val response = accountService.deposit(id, request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "계좌 출금", description = "ACTIVE 계좌에서 금액을 출금합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "출금 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "계좌를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "해지 계좌 출금 불가 또는 잔액 부족",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/{id}/withdraw")
    fun withdraw(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "withdraw-1-k1"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Parameter(description = "계좌 ID", example = "1")
        @PathVariable id: Long,
        @Valid @RequestBody request: AccountBalanceChangeRequest
    ): ResponseEntity<AccountResponse> {
        val response = accountService.withdraw(id, request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "계좌 해지", description = "잔액이 0원인 ACTIVE 계좌를 해지합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "계좌 해지 성공"),
            ApiResponse(
                responseCode = "404",
                description = "계좌를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 해지된 계좌 또는 잔액 존재",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/{id}/close")
    fun closeAccount(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "close-1-k1"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Parameter(description = "계좌 ID", example = "1")
        @PathVariable id: Long
    ): ResponseEntity<AccountResponse> {
        val response = accountService.closeAccount(id)
        return ResponseEntity.ok(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(ErrorResponse(e.message ?: "Bad Request"))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(e.message ?: "Conflict"))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.message ?: "Not Found"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Bad Request"
        return ResponseEntity.badRequest().body(ErrorResponse(message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(ErrorResponse("Bad Request"))
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("동시성 충돌이 발생했습니다. 다시 시도해주세요."))
    }
}
