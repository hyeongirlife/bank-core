package com.bankcore.common.idempotency

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class IdempotencyService(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val KEY_PREFIX = "idempotency:"
        private val TTL = Duration.ofHours(24)
    }

    fun tryAcquire(key: String): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent("$KEY_PREFIX$key", "PROCESSING", TTL) ?: false
    }

    fun getResponse(key: String): String? {
        return redisTemplate.opsForValue().get("$KEY_PREFIX$key")
    }

    fun saveResponse(key: String, response: String) {
        redisTemplate.opsForValue().set("$KEY_PREFIX$key", response, TTL)
    }
}
