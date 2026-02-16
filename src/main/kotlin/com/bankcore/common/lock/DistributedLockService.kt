package com.bankcore.common.lock

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class DistributedLockService(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private val LOCK_TTL = Duration.ofSeconds(5)
    }

    fun <T> executeWithLock(domain: String, key: String, action: () -> T): T {
        val lockKey = "lock:$domain:$key"
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "LOCKED", LOCK_TTL) ?: false

        if (!acquired) {
            throw IllegalStateException("현재 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.")
        }

        return try {
            action()
        } finally {
            redisTemplate.delete(lockKey)
        }
    }
}
