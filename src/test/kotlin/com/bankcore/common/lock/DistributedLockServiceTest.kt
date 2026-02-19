package com.bankcore.common.lock

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class DistributedLockServiceTest {

    @Mock lateinit var redisTemplate: StringRedisTemplate
    @Mock lateinit var valueOperations: ValueOperations<String, String>

    @InjectMocks lateinit var lockService: DistributedLockService

    @Test
    fun `락을 획득하고 작업을 실행한다`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.setIfAbsent(eq("lock:account:1:SAV001"), any(), any<Duration>())).thenReturn(true)
        whenever(redisTemplate.execute(any<DefaultRedisScript<Long>>(), eq(listOf("lock:account:1:SAV001")), any())).thenReturn(1L)

        val result = lockService.executeWithLock("account", "1:SAV001") { "success" }

        val lockValueCaptor = argumentCaptor<String>()
        verify(valueOperations).setIfAbsent(eq("lock:account:1:SAV001"), lockValueCaptor.capture(), any<Duration>())

        assertEquals("success", result)
        verify(redisTemplate).execute(
            any<DefaultRedisScript<Long>>(),
            eq(listOf("lock:account:1:SAV001")),
            eq(lockValueCaptor.firstValue)
        )
    }

    @Test
    fun `락 획득 실패 시 예외를 던진다`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.setIfAbsent(eq("lock:account:1:SAV001"), any(), any<Duration>())).thenReturn(false)

        assertThrows<IllegalStateException> {
            lockService.executeWithLock("account", "1:SAV001") { "success" }
        }
    }

    @Test
    fun `작업 실패 시에도 락을 해제한다`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.setIfAbsent(eq("lock:account:1:SAV001"), any(), any<Duration>())).thenReturn(true)
        whenever(redisTemplate.execute(any<DefaultRedisScript<Long>>(), eq(listOf("lock:account:1:SAV001")), any())).thenReturn(1L)

        assertThrows<RuntimeException> {
            lockService.executeWithLock("account", "1:SAV001") { throw RuntimeException("fail") }
        }

        verify(redisTemplate).execute(any<DefaultRedisScript<Long>>(), eq(listOf("lock:account:1:SAV001")), any())
    }
}
