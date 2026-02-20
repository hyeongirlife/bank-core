package com.bankcore.common.lock

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class DistributedLockService(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private val LOCK_TTL = Duration.ofSeconds(5)
        private val UNLOCK_SCRIPT = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
                """.trimIndent()
            )
            setResultType(Long::class.java)
        }
    }

    fun <T> executeWithLock(domain: String, key: String, action: () -> T): T {
        val lockKey = "lock:$domain:$key"
        val lockValue = UUID.randomUUID().toString()
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, LOCK_TTL) ?: false

        if (!acquired) {
            throw IllegalStateException("현재 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.")
        }

        var actionException: Throwable? = null

        return try {
            action()
        } catch (e: Throwable) {
            actionException = e
            throw e
        } finally {
            try {
                redisTemplate.execute(UNLOCK_SCRIPT, listOf(lockKey), lockValue)
            } catch (unlockException: Throwable) {
                if (actionException != null) {
                    actionException.addSuppressed(unlockException)
                } else {
                    throw unlockException
                }
            }
        }
    }
}
