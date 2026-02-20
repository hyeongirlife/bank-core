package com.bankcore.interest.controller

import com.bankcore.account.controller.ErrorResponse
import com.bankcore.interest.dto.DailyInterestAccrualRequest
import com.bankcore.interest.dto.DailyInterestAccrualResponse
import com.bankcore.interest.service.DailyInterestAccrualService
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
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Interests", description = "계좌 이자 API")
@RestController
@RequestMapping("/api/accounts")
class InterestController(
    private val dailyInterestAccrualService: DailyInterestAccrualService
) {
    @Operation(summary = "일별 이자 적립", description = "단일 계좌의 일별 이자를 계산해 적립 로그를 생성합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "일별 이자 적립 성공"),
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
                description = "비즈니스 충돌(금리 미설정/상태 충돌/처리 중 요청)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/{accountId}/interests/daily-accrual")
    fun accrueDailyInterest(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "interest-1-2026-02-20"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Parameter(description = "계좌 ID", example = "1")
        @PathVariable accountId: Long,
        @Valid @RequestBody request: DailyInterestAccrualRequest
    ): ResponseEntity<DailyInterestAccrualResponse> {
        val response = dailyInterestAccrualService.accrueDailyInterest(accountId, request)
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
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: e.bindingResult.globalErrors.firstOrNull()?.defaultMessage
            ?: "Bad Request"
        return ResponseEntity.badRequest().body(ErrorResponse(message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(ErrorResponse("Bad Request"))
    }
}
