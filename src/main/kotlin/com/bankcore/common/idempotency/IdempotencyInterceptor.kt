package com.bankcore.common.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
class IdempotencyInterceptor(
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper
) : HandlerInterceptor {

    companion object {
        const val HEADER_NAME = "Idempotency-Key"
        const val ATTRIBUTE_KEY = "idempotencyKey"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "GET") return true

        val key = request.getHeader(HEADER_NAME) ?: return true

        if (idempotencyService.tryAcquire(key)) {
            request.setAttribute(ATTRIBUTE_KEY, key)
            return true
        }

        val savedResponse = idempotencyService.getResponse(key)
        if (savedResponse == null || savedResponse == "PROCESSING") {
            response.status = 409
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(objectMapper.writeValueAsString(mapOf("error" to "요청이 처리 중입니다")))
            return false
        }

        response.status = 200
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(savedResponse)
        return false
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val key = request.getAttribute(ATTRIBUTE_KEY) as? String ?: return

        if (ex == null && response.status in 200..299) {
            val wrapper = response as? ContentCachingResponseWrapper
            val body = wrapper?.contentAsByteArray?.let { String(it) }
            if (body != null) {
                idempotencyService.saveResponse(key, body)
            }
        }
    }
}
