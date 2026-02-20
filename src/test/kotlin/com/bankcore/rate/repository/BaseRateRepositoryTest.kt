package com.bankcore.rate.repository

import com.bankcore.rate.entity.BaseRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate

@ActiveProfiles("local")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BaseRateRepositoryTest {

    @Autowired
    lateinit var baseRateRepository: BaseRateRepository

    @Test
    fun `영업일로 기준금리를 조회한다`() {
        val businessDate = LocalDate.of(2026, 2, 20)

        baseRateRepository.save(
            BaseRate(
                businessDate = businessDate,
                rate = BigDecimal("0.0300")
            )
        )

        val found = baseRateRepository.findByBusinessDate(businessDate)

        assertEquals(BigDecimal("0.0300"), found?.rate)
    }

    @Test
    fun `존재하지 않는 영업일 조회 시 null을 반환한다`() {
        val found = baseRateRepository.findByBusinessDate(LocalDate.of(2026, 2, 21))

        assertNull(found)
    }

    @Test
    fun `동일 영업일 기준금리 중복 저장 시 예외가 발생한다`() {
        val businessDate = LocalDate.of(2026, 2, 22)

        baseRateRepository.saveAndFlush(
            BaseRate(
                businessDate = businessDate,
                rate = BigDecimal("0.0300")
            )
        )

        assertThrows<DataIntegrityViolationException> {
            baseRateRepository.saveAndFlush(
                BaseRate(
                    businessDate = businessDate,
                    rate = BigDecimal("0.0310")
                )
            )
        }
    }
}
