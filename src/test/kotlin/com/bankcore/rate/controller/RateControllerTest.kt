package com.bankcore.rate.controller

import com.bankcore.common.idempotency.IdempotencyService
import com.bankcore.rate.dto.BaseRateUpsertRequest
import com.bankcore.rate.dto.ComposedRateResponse
import com.bankcore.rate.dto.PreferentialRateUpsertRequest
import com.bankcore.rate.dto.SpreadRateUpsertRequest
import com.bankcore.rate.service.RateService
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(RateController::class)
class RateControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @MockitoBean lateinit var rateService: RateService
    @MockitoBean lateinit var idempotencyService: IdempotencyService

    @Test
    fun `기준금리 등록 요청 시 201을 반환한다`() {
        val request = BaseRateUpsertRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0300")
        )

        whenever(rateService.upsertBaseRate(any())).thenReturn(request)

        mockMvc.post("/api/rates/base") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.rate") { value(0.03) }
        }
    }

    @Test
    fun `가산금리 등록 요청 시 201을 반환한다`() {
        val request = SpreadRateUpsertRequest(
            productCode = "SAV001",
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0010")
        )

        whenever(rateService.upsertSpreadRate(any())).thenReturn(request)

        mockMvc.post("/api/rates/spread") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.productCode") { value("SAV001") }
            jsonPath("$.rate") { value(0.001) }
        }
    }

    @Test
    fun `우대금리 등록 요청 시 201을 반환한다`() {
        val request = PreferentialRateUpsertRequest(
            productCode = "SAV001",
            conditionCode = "SALARY_TRANSFER",
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0020")
        )

        whenever(rateService.upsertPreferentialRate(any())).thenReturn(request)

        mockMvc.post("/api/rates/preferential") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.conditionCode") { value("SALARY_TRANSFER") }
            jsonPath("$.rate") { value(0.002) }
        }
    }

    @Test
    fun `복합금리 조회 시 200을 반환한다`() {
        val response = ComposedRateResponse(
            productCode = "SAV001",
            businessDate = LocalDate.of(2026, 2, 20),
            conditionCodes = listOf("SALARY_TRANSFER"),
            baseRate = BigDecimal("0.0300"),
            spreadRate = BigDecimal("0.0010"),
            preferentialRate = BigDecimal("0.0020"),
            appliedRate = BigDecimal("0.0330")
        )

        whenever(
            rateService.getComposedRate(
                productCode = "SAV001",
                businessDate = LocalDate.of(2026, 2, 20),
                conditionCodes = listOf("SALARY_TRANSFER")
            )
        ).thenReturn(response)

        mockMvc.get("/api/rates/composed") {
            param("productCode", "SAV001")
            param("businessDate", "2026-02-20")
            param("conditionCodes", "SALARY_TRANSFER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.appliedRate") { value(0.033) }
        }
    }

    @Test
    fun `요청 검증 실패 시 400을 반환한다`() {
        val request = BaseRateUpsertRequest(
            businessDate = LocalDate.now().plusDays(1),
            rate = BigDecimal("0.0300")
        )

        mockMvc.post("/api/rates/base") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("영업일")) }
        }
    }

    @Test
    fun `상품 미존재면 404를 반환한다`() {
        whenever(
            rateService.getComposedRate(
                productCode = "MISSING",
                businessDate = LocalDate.of(2026, 2, 20),
                conditionCodes = emptyList()
            )
        ).thenThrow(NoSuchElementException("상품을 찾을 수 없습니다: MISSING"))

        mockMvc.get("/api/rates/composed") {
            param("productCode", "MISSING")
            param("businessDate", "2026-02-20")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value(containsString("상품")) }
        }
    }

    @Test
    fun `복합금리 조회 시 상품코드 blank면 400을 반환한다`() {
        mockMvc.get("/api/rates/composed") {
            param("productCode", "")
            param("businessDate", "2026-02-20")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value(containsString("상품 코드")) }
        }
    }

    @Test
    fun `중복 금리 등록 시 409를 반환한다`() {
        val request = BaseRateUpsertRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0300")
        )

        whenever(rateService.upsertBaseRate(any())).thenThrow(IllegalStateException("이미 기준금리가 존재합니다"))

        mockMvc.post("/api/rates/base") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value(containsString("기준금리")) }
        }
    }
}
