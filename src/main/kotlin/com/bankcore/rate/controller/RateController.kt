package com.bankcore.rate.controller

import com.bankcore.account.controller.ErrorResponse
import com.bankcore.rate.dto.BaseRateUpsertRequest
import com.bankcore.rate.dto.ComposedRateResponse
import com.bankcore.rate.dto.PreferentialRateUpsertRequest
import com.bankcore.rate.dto.SpreadRateUpsertRequest
import com.bankcore.rate.service.RateService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.validation.annotation.Validated
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "Rates", description = "금리 관리 API")
@Validated
@RestController
@RequestMapping("/api/rates")
class RateController(
    private val rateService: RateService
) {
    @Operation(summary = "기준금리 등록", description = "영업일 기준 기준금리를 등록합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "기준금리 등록 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "중복 기준금리 등록 충돌",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/base")
    fun upsertBaseRate(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "rate-base-2026-02-20"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: BaseRateUpsertRequest
    ): ResponseEntity<BaseRateUpsertRequest> {
        val response = rateService.upsertBaseRate(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "가산금리 등록", description = "상품/영업일 기준 가산금리를 등록합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "가산금리 등록 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "상품을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "중복 가산금리 등록 충돌",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/spread")
    fun upsertSpreadRate(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "rate-spread-SAV001-2026-02-20"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: SpreadRateUpsertRequest
    ): ResponseEntity<SpreadRateUpsertRequest> {
        val response = rateService.upsertSpreadRate(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "우대금리 등록", description = "상품/조건코드/영업일 기준 우대금리를 등록합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "우대금리 등록 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "상품을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "중복 우대금리 등록 충돌",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/preferential")
    fun upsertPreferentialRate(
        @Parameter(
            name = "Idempotency-Key",
            description = "멱등성 키 (같은 키로 재시도 시 동일 응답 반환)",
            required = false,
            `in` = ParameterIn.HEADER,
            example = "rate-pref-SAV001-SALARY_TRANSFER-2026-02-20"
        )
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PreferentialRateUpsertRequest
    ): ResponseEntity<PreferentialRateUpsertRequest> {
        val response = rateService.upsertPreferentialRate(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "복합금리 조회", description = "기준+가산+우대금리를 조합한 적용금리를 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "복합금리 조회 성공"),
            ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "상품 또는 기준금리를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @GetMapping("/composed")
    fun getComposedRate(
        @Parameter(description = "상품 코드", example = "SAV001")
        @RequestParam @NotBlank(message = "상품 코드는 필수입니다") productCode: String,
        @Parameter(description = "영업일", example = "2026-02-20")
        @RequestParam businessDate: LocalDate,
        @Parameter(description = "우대 조건 코드 목록", example = "SALARY_TRANSFER")
        @RequestParam(required = false, defaultValue = "") conditionCodes: List<String>
    ): ResponseEntity<ComposedRateResponse> {
        val normalizedConditionCodes = conditionCodes.filter { it.isNotBlank() }
        val response = rateService.getComposedRate(productCode, businessDate, normalizedConditionCodes)
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

    @ExceptionHandler(jakarta.validation.ConstraintViolationException::class)
    fun handleConstraintViolation(e: jakarta.validation.ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val message = e.constraintViolations.firstOrNull()?.message ?: "Bad Request"
        return ResponseEntity.badRequest().body(ErrorResponse(message))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("이미 등록된 금리입니다"))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(ErrorResponse("Bad Request"))
    }
}
