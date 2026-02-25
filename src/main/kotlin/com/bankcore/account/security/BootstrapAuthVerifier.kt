package com.bankcore.account.security

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class BootstrapAuthVerifier(
    @Value("\${account.bootstrap.auth-signing-secret}") private val signingSecret: String
) {
    companion object {
        const val CUSTOMER_HEADER = "X-Customer-Id"
        const val TIMESTAMP_HEADER = "X-Customer-Timestamp"
        const val SIGNATURE_HEADER = "X-Customer-Signature"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val TIMESTAMP_WINDOW_SECONDS = 300L
        private const val SIGNING_SECRET_MIN_LENGTH = 32
    }

    @PostConstruct
    fun validateSigningSecret() {
        require(signingSecret.isNotBlank()) { "account.bootstrap.auth-signing-secret 는 필수입니다" }
        require(signingSecret.length >= SIGNING_SECRET_MIN_LENGTH) {
            "account.bootstrap.auth-signing-secret 는 ${SIGNING_SECRET_MIN_LENGTH}자 이상이어야 합니다"
        }
    }

    fun verifyOrThrow(customerIdHeader: String?, timestampHeader: String?, signatureHeader: String?) {
        if (customerIdHeader.isNullOrBlank()) {
            throw IllegalArgumentException("요청 헤더가 누락되었습니다: $CUSTOMER_HEADER")
        }
        if (timestampHeader.isNullOrBlank()) {
            throw IllegalArgumentException("요청 헤더가 누락되었습니다: $TIMESTAMP_HEADER")
        }
        if (signatureHeader.isNullOrBlank()) {
            throw IllegalArgumentException("요청 헤더가 누락되었습니다: $SIGNATURE_HEADER")
        }

        val customerId = customerIdHeader.toLongOrNull()
            ?: throw IllegalArgumentException("요청 헤더 형식이 올바르지 않습니다: $CUSTOMER_HEADER")
        if (customerId < 1L) {
            throw IllegalArgumentException("고객 ID는 1 이상이어야 합니다")
        }

        val timestamp = timestampHeader.toLongOrNull()
            ?: throw IllegalArgumentException("요청 헤더 형식이 올바르지 않습니다: $TIMESTAMP_HEADER")
        if (!isTimestampValid(timestamp)) {
            throw IllegalArgumentException("요청 인증 시간이 유효하지 않습니다")
        }

        val expected = sign("$customerId:$timestamp")
        if (!constantTimeEquals(expected, signatureHeader)) {
            throw IllegalArgumentException("요청 인증 서명이 유효하지 않습니다")
        }
    }

    private fun isTimestampValid(timestamp: Long): Boolean {
        val now = System.currentTimeMillis() / 1000
        val diff = kotlin.math.abs(now - timestamp)
        return diff <= TIMESTAMP_WINDOW_SECONDS
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingSecret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        val bytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun constantTimeEquals(expectedHex: String, providedHex: String): Boolean {
        val expected = expectedHex.toByteArray(StandardCharsets.UTF_8)
        val provided = providedHex.lowercase().toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expected, provided)
    }
}
