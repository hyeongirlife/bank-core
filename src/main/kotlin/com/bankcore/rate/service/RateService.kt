package com.bankcore.rate.service

import com.bankcore.product.repository.ProductRepository
import com.bankcore.rate.dto.BaseRateUpsertRequest
import com.bankcore.rate.dto.ComposedRateResponse
import com.bankcore.rate.dto.PreferentialRateUpsertRequest
import com.bankcore.rate.dto.SpreadRateUpsertRequest
import com.bankcore.rate.entity.BaseRate
import com.bankcore.rate.entity.PreferentialRate
import com.bankcore.rate.entity.SpreadRate
import com.bankcore.rate.repository.BaseRateRepository
import com.bankcore.rate.repository.PreferentialRateRepository
import com.bankcore.rate.repository.SpreadRateRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class RateService(
    private val productRepository: ProductRepository,
    private val baseRateRepository: BaseRateRepository,
    private val spreadRateRepository: SpreadRateRepository,
    private val preferentialRateRepository: PreferentialRateRepository
) {
    @Transactional
    fun upsertBaseRate(request: BaseRateUpsertRequest): BaseRateUpsertRequest {
        val existing = baseRateRepository.findByBusinessDate(request.businessDate)
        if (existing != null) {
            throw IllegalStateException("이미 기준금리가 존재합니다: ${request.businessDate}")
        }

        try {
            baseRateRepository.save(
                BaseRate(
                    businessDate = request.businessDate,
                    rate = request.rate.setScale(4, RoundingMode.DOWN)
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw IllegalStateException("이미 기준금리가 존재합니다: ${request.businessDate}")
        }
        return request
    }

    @Transactional
    fun upsertSpreadRate(request: SpreadRateUpsertRequest): SpreadRateUpsertRequest {
        val normalizedProductCode = request.productCode.trim()
        val product = productRepository.findByCode(normalizedProductCode)
            ?: throw NoSuchElementException("상품을 찾을 수 없습니다: $normalizedProductCode")

        val existing = spreadRateRepository.findByProductCodeAndBusinessDateAndIsActiveTrue(
            normalizedProductCode,
            request.businessDate
        )
        if (existing != null) {
            throw IllegalStateException("이미 가산금리가 존재합니다: $normalizedProductCode, ${request.businessDate}")
        }

        try {
            spreadRateRepository.save(
                SpreadRate(
                    product = product,
                    businessDate = request.businessDate,
                    rate = request.rate.setScale(4, RoundingMode.DOWN),
                    isActive = true
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw IllegalStateException("이미 가산금리가 존재합니다: $normalizedProductCode, ${request.businessDate}")
        }

        return request.copy(productCode = normalizedProductCode)
    }

    @Transactional
    fun upsertPreferentialRate(request: PreferentialRateUpsertRequest): PreferentialRateUpsertRequest {
        val normalizedProductCode = request.productCode.trim()
        val normalizedConditionCode = request.conditionCode.trim()

        val product = productRepository.findByCode(normalizedProductCode)
            ?: throw NoSuchElementException("상품을 찾을 수 없습니다: $normalizedProductCode")

        val existing = preferentialRateRepository.findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
            normalizedProductCode,
            listOf(normalizedConditionCode),
            request.businessDate
        )
        if (existing.isNotEmpty()) {
            throw IllegalStateException(
                "이미 우대금리가 존재합니다: $normalizedProductCode, $normalizedConditionCode, ${request.businessDate}"
            )
        }

        try {
            preferentialRateRepository.save(
                PreferentialRate(
                    product = product,
                    conditionCode = normalizedConditionCode,
                    businessDate = request.businessDate,
                    rate = request.rate.setScale(4, RoundingMode.DOWN),
                    isActive = true
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw IllegalStateException(
                "이미 우대금리가 존재합니다: $normalizedProductCode, $normalizedConditionCode, ${request.businessDate}"
            )
        }

        return request.copy(productCode = normalizedProductCode, conditionCode = normalizedConditionCode)
    }

    @Transactional(readOnly = true)
    fun getComposedRate(productCode: String, businessDate: LocalDate, conditionCodes: List<String>): ComposedRateResponse {
        val normalizedProductCode = productCode.trim()
        productRepository.findByCode(normalizedProductCode)
            ?: throw NoSuchElementException("상품을 찾을 수 없습니다: $normalizedProductCode")

        val baseRate = baseRateRepository.findByBusinessDate(businessDate)
            ?: throw NoSuchElementException("기준금리를 찾을 수 없습니다: $businessDate")

        val spreadRate = spreadRateRepository
            .findByProductCodeAndBusinessDateAndIsActiveTrue(normalizedProductCode, businessDate)
            ?.rate
            ?.setScale(4, RoundingMode.DOWN)
            ?: BigDecimal.ZERO.setScale(4, RoundingMode.DOWN)

        val normalizedConditionCodes = conditionCodes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val preferentialRate = if (normalizedConditionCodes.isEmpty()) {
            BigDecimal.ZERO.setScale(4, RoundingMode.DOWN)
        } else {
            preferentialRateRepository
                .findAllByProductCodeAndConditionCodeInAndBusinessDateAndIsActiveTrue(
                    normalizedProductCode,
                    normalizedConditionCodes,
                    businessDate
                )
                .fold(BigDecimal.ZERO) { acc, rate -> acc.add(rate.rate) }
                .setScale(4, RoundingMode.DOWN)
        }

        val appliedRate = baseRate.rate
            .setScale(4, RoundingMode.DOWN)
            .add(spreadRate)
            .add(preferentialRate)
            .setScale(4, RoundingMode.DOWN)

        return ComposedRateResponse(
            productCode = normalizedProductCode,
            businessDate = businessDate,
            conditionCodes = normalizedConditionCodes,
            baseRate = baseRate.rate.setScale(4, RoundingMode.DOWN),
            spreadRate = spreadRate,
            preferentialRate = preferentialRate,
            appliedRate = appliedRate
        )
    }
}
