package com.bankcore.account.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BootstrapAuthVerifierTest {

    companion object {
        private const val SECRET = "test-bootstrap-signing-secret-0123456789"
        private const val CUSTOMER_ID = "1"
    }

    private val verifier = BootstrapAuthVerifier(SECRET).apply { validateSigningSecret() }

    @Test
    fun `유효한 헤더 조합이면 검증을 통과한다`() {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = sign("$CUSTOMER_ID:$timestamp")

        assertDoesNotThrow {
            verifier.verifyOrThrow(CUSTOMER_ID, timestamp, signature)
        }
    }

    @Test
    fun `customer 헤더가 누락되면 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow(null, "1739942400", "sig")
        }

        assertEquals("요청 헤더가 누락되었습니다: X-Customer-Id", ex.message)
    }

    @Test
    fun `timestamp 헤더가 누락되면 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow(CUSTOMER_ID, null, "sig")
        }

        assertEquals("요청 헤더가 누락되었습니다: X-Customer-Timestamp", ex.message)
    }

    @Test
    fun `signature 헤더가 누락되면 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow(CUSTOMER_ID, "1739942400", null)
        }

        assertEquals("요청 헤더가 누락되었습니다: X-Customer-Signature", ex.message)
    }

    @Test
    fun `customer 헤더가 숫자가 아니면 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow("abc", "1739942400", "sig")
        }

        assertEquals("요청 헤더 형식이 올바르지 않습니다: X-Customer-Id", ex.message)
    }

    @Test
    fun `customer 헤더가 1 미만이면 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow("0", "1739942400", "sig")
        }

        assertEquals("고객 ID는 1 이상이어야 합니다", ex.message)
    }

    @Test
    fun `timestamp 헤더가 숫자가 아니면 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow(CUSTOMER_ID, "abc", "sig")
        }

        assertEquals("요청 헤더 형식이 올바르지 않습니다: X-Customer-Timestamp", ex.message)
    }

    @Test
    fun `timestamp가 과거 허용 시간 범위를 벗어나면 예외를 던진다`() {
        val oldTimestamp = ((System.currentTimeMillis() / 1000) - 301).toString()
        val signature = sign("$CUSTOMER_ID:$oldTimestamp")

        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow(CUSTOMER_ID, oldTimestamp, signature)
        }

        assertEquals("요청 인증 시간이 유효하지 않습니다", ex.message)
    }

    @Test
    fun `timestamp가 미래 허용 시간 범위를 벗어나면 예외를 던진다`() {
        val futureTimestamp = ((System.currentTimeMillis() / 1000) + 301).toString()
        val signature = sign("$CUSTOMER_ID:$futureTimestamp")

        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow(CUSTOMER_ID, futureTimestamp, signature)
        }

        assertEquals("요청 인증 시간이 유효하지 않습니다", ex.message)
    }

    @Test
    fun `서명이 대문자 hex여도 검증을 통과한다`() {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = sign("$CUSTOMER_ID:$timestamp").uppercase()

        assertDoesNotThrow {
            verifier.verifyOrThrow(CUSTOMER_ID, timestamp, signature)
        }
    }

    @Test
    fun `signature가 유효하지 않으면 예외를 던진다`() {
        val timestamp = (System.currentTimeMillis() / 1000).toString()

        val ex = assertThrows<IllegalArgumentException> {
            verifier.verifyOrThrow(CUSTOMER_ID, timestamp, "invalid-signature")
        }

        assertEquals("요청 인증 서명이 유효하지 않습니다", ex.message)
    }

    @Test
    fun `서명 시크릿이 32자 미만이면 초기화에서 예외를 던진다`() {
        val ex = assertThrows<IllegalArgumentException> {
            BootstrapAuthVerifier("short-secret").validateSigningSecret()
        }

        assertEquals("account.bootstrap.auth-signing-secret 는 32자 이상이어야 합니다", ex.message)
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256"))
        val bytes = mac.doFinal(payload.toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
