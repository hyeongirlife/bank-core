package com.bankcore.common.idempotency

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
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
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        idempotencyService = IdempotencyService(redisTemplate)
    }

    @Test
    fun `should acquire lock when key does not exist`() {
        whenever(valueOperations.setIfAbsent(eq("idempotency:abc-123"), eq("PROCESSING"), any<Duration>()))
            .thenReturn(true)

        val result = idempotencyService.tryAcquire("abc-123")

        assertTrue(result)
    }

    @Test
    fun `should fail to acquire lock when key already exists`() {
        whenever(valueOperations.setIfAbsent(eq("idempotency:abc-123"), eq("PROCESSING"), any<Duration>()))
            .thenReturn(false)

        val result = idempotencyService.tryAcquire("abc-123")

        assertFalse(result)
    }

    @Test
    fun `should return saved response when exists`() {
        whenever(valueOperations.get("idempotency:abc-123")).thenReturn("{\"id\":1}")

        val result = idempotencyService.getResponse("abc-123")

        assertEquals("{\"id\":1}", result)
    }

    @Test
    fun `should return null when no saved response`() {
        whenever(valueOperations.get("idempotency:abc-123")).thenReturn(null)

        val result = idempotencyService.getResponse("abc-123")

        assertNull(result)
    }

    @Test
    fun `should return PROCESSING as saved response during processing`() {
        whenever(valueOperations.get("idempotency:abc-123")).thenReturn("PROCESSING")

        val result = idempotencyService.getResponse("abc-123")

        assertEquals("PROCESSING", result)
    }

    @Test
    fun `should save response with TTL`() {
        idempotencyService.saveResponse("abc-123", "{\"id\":1}")

        verify(valueOperations).set(eq("idempotency:abc-123"), eq("{\"id\":1}"), any<Duration>())
    }
}
