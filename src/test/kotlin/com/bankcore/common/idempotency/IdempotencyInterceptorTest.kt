package com.bankcore.common.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@ExtendWith(MockitoExtension::class)
class IdempotencyInterceptorTest {

    @Mock lateinit var idempotencyService: IdempotencyService

    lateinit var interceptor: IdempotencyInterceptor
    lateinit var request: MockHttpServletRequest
    lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        interceptor = IdempotencyInterceptor(idempotencyService, ObjectMapper())
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
    }

    @Test
    fun `should pass through when no idempotency key header`() {
        request.method = "POST"

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }

    @Test
    fun `should pass through for GET requests`() {
        request.method = "GET"
        request.addHeader("Idempotency-Key", "abc-123")

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }

    @Test
    fun `should acquire lock and proceed when key is new`() {
        request.method = "POST"
        request.addHeader("Idempotency-Key", "abc-123")

        whenever(idempotencyService.tryAcquire("abc-123")).thenReturn(true)

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }

    @Test
    fun `should return cached response when key already processed`() {
        request.method = "POST"
        request.addHeader("Idempotency-Key", "abc-123")

        whenever(idempotencyService.tryAcquire("abc-123")).thenReturn(false)
        whenever(idempotencyService.getResponse("abc-123"))
            .thenReturn("""{"id":1,"accountNumber":"110-123-456789"}""")

        val result = interceptor.preHandle(request, response, Any())

        assertFalse(result)
        assertEquals(200, response.status)
        assertEquals("application/json", response.contentType)
        assertTrue(response.contentAsString.contains("110-123-456789"))
    }

    @Test
    fun `should return 409 when key is still processing`() {
        request.method = "POST"
        request.addHeader("Idempotency-Key", "abc-123")

        whenever(idempotencyService.tryAcquire("abc-123")).thenReturn(false)
        whenever(idempotencyService.getResponse("abc-123")).thenReturn("PROCESSING")

        val result = interceptor.preHandle(request, response, Any())

        assertFalse(result)
        assertEquals(409, response.status)
    }
}
