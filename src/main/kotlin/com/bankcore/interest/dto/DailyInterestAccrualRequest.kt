package com.bankcore.interest.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "일별 이자 적립 요청")
data class DailyInterestAccrualRequest(
    @field:Schema(description = "영업일", example = "2026-02-20")
    @field:PastOrPresent(message = "영업일은 미래일 수 없습니다")
    val businessDate: LocalDate,

    @field:Schema(description = "우대금리 조건 코드 목록", example = "[\"SALARY_TRANSFER\", \"CARD_USAGE\"]")
    @field:Size(max = 20, message = "조건코드는 최대 20개까지 입력할 수 있습니다")
    val conditionCodes: List<String> = emptyList()
) {
    @get:AssertTrue(message = "조건코드는 공백일 수 없습니다")
    val hasNoBlankConditionCode: Boolean
        get() = conditionCodes.all { it.isNotBlank() }

    @get:AssertTrue(message = "조건코드는 중복될 수 없습니다")
    val hasUniqueConditionCodes: Boolean
        get() {
            val normalized = conditionCodes.map { it.trim() }
            return normalized.size == normalized.toSet().size
        }

    @get:AssertTrue(message = "조건코드는 영문 대문자와 언더스코어만 허용됩니다")
    val hasValidConditionCodePattern: Boolean
        get() = conditionCodes.all { CONDITION_CODE_REGEX.matches(it.trim()) }

    companion object {
        private val CONDITION_CODE_REGEX = Regex("^[A-Z_]{1,50}$")
    }
}
