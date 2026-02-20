package com.bankcore.rate.service

import com.bankcore.product.entity.Product
import com.bankcore.product.repository.ProductRepository
import com.bankcore.rate.dto.BaseRateUpsertRequest
import com.bankcore.rate.dto.PreferentialRateUpsertRequest
import com.bankcore.rate.dto.SpreadRateUpsertRequest
import com.bankcore.rate.entity.BaseRate
import com.bankcore.rate.entity.PreferentialRate
import com.bankcore.rate.entity.SpreadRate
import com.bankcore.rate.repository.BaseRateRepository
import com.bankcore.rate.repository.PreferentialRateRepository
import com.bankcore.rate.repository.SpreadRateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class RateServiceTest {

    @Mock lateinit var productRepository: ProductRepository
    @Mock lateinit var baseRateRepository: BaseRateRepository
    @Mock lateinit var spreadRateRepository: SpreadRateRepository
    @Mock lateinit var preferentialRateRepository: PreferentialRateRepository

    @InjectMocks lateinit var rateService: RateService

    private val product = Product(id = 1L, code = "SAV001", name = "Basic Savings")

    @Test
    fun `기준금리 등록 성공`() {
        val request = BaseRateUpsertRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0300")
        )

        whenever(baseRateRepository.findByBusinessDate(request.businessDate)).thenReturn(null)
        whenever(baseRateRepository.save(any<BaseRate>())).thenAnswer { it.arguments[0] as BaseRate }

        val result = rateService.upsertBaseRate(request)

        assertEquals(BigDecimal("0.0300"), result.rate)
    }

    @Test
    fun `기준금리 중복 등록 시 예외`() {
        val request = BaseRateUpsertRequest(
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0300")
        )

        whenever(baseRateRepository.findByBusinessDate(request.businessDate)).thenReturn(
            BaseRate(id = 1L, businessDate = request.businessDate, rate = BigDecimal("0.0290"))
        )

        assertThrows<IllegalStateException> {
            rateService.upsertBaseRate(request)
        }
    }

    @Test
    fun `기준금리 저장 중 유니크 충돌 시 예외`() {
        val request = BaseRateUpsertRequest(
            businessDate = LocalDate.of(2026, 2, 21),
            rate = BigDecimal("0.0310")
        )

        whenever(baseRateRepository.findByBusinessDate(request.businessDate)).thenReturn(null)
        whenever(baseRateRepository.save(any<BaseRate>())).thenThrow(DataIntegrityViolationException("unique"))

        assertThrows<IllegalStateException> {
            rateService.upsertBaseRate(request)
        }
    }

    @Test
    fun `가산금리 등록 성공`() {
        val request = SpreadRateUpsertRequest(
            productCode = "SAV001",
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0010")
        )

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue("SAV001", request.businessDate)).thenReturn(null)
        whenever(spreadRateRepository.save(any<SpreadRate>())).thenAnswer { it.arguments[0] as SpreadRate }

        val result = rateService.upsertSpreadRate(request)

        assertEquals("SAV001", result.productCode)
        assertEquals(BigDecimal("0.0010"), result.rate)
    }

    @Test
    fun `가산금리 저장 중 유니크 충돌 시 예외`() {
        val request = SpreadRateUpsertRequest(
            productCode = "SAV001",
            businessDate = LocalDate.of(2026, 2, 21),
            rate = BigDecimal("0.0010")
        )

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue("SAV001", request.businessDate)).thenReturn(null)
        whenever(spreadRateRepository.save(any<SpreadRate>())).thenThrow(DataIntegrityViolationException("unique"))

        assertThrows<IllegalStateException> {
            rateService.upsertSpreadRate(request)
        }
    }

    @Test
    fun `우대금리 등록 성공`() {
        val request = PreferentialRateUpsertRequest(
            productCode = "SAV001",
            conditionCode = "SALARY_TRANSFER",
            businessDate = LocalDate.of(2026, 2, 20),
            rate = BigDecimal("0.0020")
        )

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(preferentialRateRepository.findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
            "SAV001",
            listOf("SALARY_TRANSFER"),
            request.businessDate
        )).thenReturn(emptyList())
        whenever(preferentialRateRepository.save(any<PreferentialRate>())).thenAnswer { it.arguments[0] as PreferentialRate }

        val result = rateService.upsertPreferentialRate(request)

        assertEquals("SALARY_TRANSFER", result.conditionCode)
        assertEquals(BigDecimal("0.0020"), result.rate)
    }

    @Test
    fun `우대금리 저장 중 유니크 충돌 시 예외`() {
        val request = PreferentialRateUpsertRequest(
            productCode = "SAV001",
            conditionCode = "SALARY_TRANSFER",
            businessDate = LocalDate.of(2026, 2, 21),
            rate = BigDecimal("0.0020")
        )

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(preferentialRateRepository.findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
            "SAV001",
            listOf("SALARY_TRANSFER"),
            request.businessDate
        )).thenReturn(emptyList())
        whenever(preferentialRateRepository.save(any<PreferentialRate>())).thenThrow(DataIntegrityViolationException("unique"))

        assertThrows<IllegalStateException> {
            rateService.upsertPreferentialRate(request)
        }
    }

    @Test
    fun `복합금리 조회 성공`() {
        val businessDate = LocalDate.of(2026, 2, 20)

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(baseRateRepository.findByBusinessDate(businessDate)).thenReturn(
            BaseRate(id = 1L, businessDate = businessDate, rate = BigDecimal("0.0300"))
        )
        whenever(spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue("SAV001", businessDate)).thenReturn(
            SpreadRate(id = 1L, product = product, businessDate = businessDate, rate = BigDecimal("0.0010"), isActive = true)
        )
        whenever(preferentialRateRepository.findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
            "SAV001",
            listOf("SALARY_TRANSFER", "CARD_USAGE"),
            businessDate
        )).thenReturn(
            listOf(
                PreferentialRate(
                    id = 1L,
                    product = product,
                    conditionCode = "SALARY_TRANSFER",
                    businessDate = businessDate,
                    rate = BigDecimal("0.0020"),
                    isActive = true
                ),
                PreferentialRate(
                    id = 2L,
                    product = product,
                    conditionCode = "CARD_USAGE",
                    businessDate = businessDate,
                    rate = BigDecimal("0.0010"),
                    isActive = true
                )
            )
        )

        val result = rateService.getComposedRate("SAV001", businessDate, listOf("SALARY_TRANSFER", "CARD_USAGE"))

        assertEquals(BigDecimal("0.0300"), result.baseRate)
        assertEquals(BigDecimal("0.0010"), result.spreadRate)
        assertEquals(BigDecimal("0.0030"), result.preferentialRate)
        assertEquals(BigDecimal("0.0340"), result.appliedRate)
    }

    @Test
    fun `복합금리 조회 시 기준금리 누락이면 예외`() {
        val businessDate = LocalDate.of(2026, 2, 20)

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(baseRateRepository.findByBusinessDate(businessDate)).thenReturn(null)

        val ex = assertThrows<NoSuchElementException> {
            rateService.getComposedRate("SAV001", businessDate, emptyList())
        }

        assertEquals("기준금리를 찾을 수 없습니다: 2026-02-20", ex.message)
    }

    @Test
    fun `복합금리 조회 시 가산금리 누락이면 0으로 처리`() {
        val businessDate = LocalDate.of(2026, 2, 20)

        whenever(productRepository.findByCode("SAV001")).thenReturn(product)
        whenever(baseRateRepository.findByBusinessDate(businessDate)).thenReturn(
            BaseRate(id = 1L, businessDate = businessDate, rate = BigDecimal("0.0300"))
        )
        whenever(spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue("SAV001", businessDate)).thenReturn(null)

        val result = rateService.getComposedRate("SAV001", businessDate, emptyList())

        assertEquals(BigDecimal("0.0000"), result.spreadRate)
        assertEquals(BigDecimal("0.0300"), result.appliedRate)
        verify(preferentialRateRepository, never()).findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(any(), any(), any())
    }
}
