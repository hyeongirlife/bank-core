package com.bankcore.common.idempotency

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class IdempotencyServiceTest {

    @Mock lateinit var redisTemplate: StringRedisTemplate
    @Mock lateinit var valueOperations: ValueOperations<String, String>

    lateinit var idempotencyService: IdempotencyService

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        idempotencyService = IdempotencyService(redisTemplate)
    }

    @Test
    fun `키가 존재하지 않으면 락을 획득한다`() {
        whenever(valueOperations.setIfAbsent(eq("idempotency:POST:/api/accounts:abc-123"), eq("PROCESSING"), any<Duration>()))
            .thenReturn(true)

        val result = idempotencyService.tryAcquire("POST:/api/accounts:abc-123")

        assertTrue(result)
    }

    @Test
    fun `키가 이미 존재하면 락 획득에 실패한다`() {
        whenever(valueOperations.setIfAbsent(eq("idempotency:POST:/api/accounts:abc-123"), eq("PROCESSING"), any<Duration>()))
            .thenReturn(false)

        val result = idempotencyService.tryAcquire("POST:/api/accounts:abc-123")

        assertFalse(result)
    }

    @Test
    fun `저장된 응답이 있으면 반환한다`() {
        whenever(valueOperations.get("idempotency:POST:/api/accounts:abc-123")).thenReturn("{\"id\":1}")

        val result = idempotencyService.getResponse("POST:/api/accounts:abc-123")

        assertEquals("{\"id\":1}", result)
    }

    @Test
    fun `저장된 응답이 없으면 null을 반환한다`() {
        whenever(valueOperations.get("idempotency:POST:/api/accounts:abc-123")).thenReturn(null)

        val result = idempotencyService.getResponse("POST:/api/accounts:abc-123")

        assertNull(result)
    }

    @Test
    fun `처리 중일 때 PROCESSING을 반환한다`() {
        whenever(valueOperations.get("idempotency:POST:/api/accounts:abc-123")).thenReturn("PROCESSING")

        val result = idempotencyService.getResponse("POST:/api/accounts:abc-123")

        assertEquals("PROCESSING", result)
    }

    @Test
    fun `응답을 TTL과 함께 저장한다`() {
        idempotencyService.saveResponse("POST:/api/accounts:abc-123", "{\"id\":1}")

        verify(valueOperations).set(eq("idempotency:POST:/api/accounts:abc-123"), eq("{\"id\":1}"), any<Duration>())
    }

    @Test
    fun `키를 삭제한다`() {
        idempotencyService.clear("POST:/api/accounts:abc-123")

        verify(redisTemplate).delete("idempotency:POST:/api/accounts:abc-123")
    }
}
