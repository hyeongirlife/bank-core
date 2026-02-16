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
    fun `멱등성 키 헤더가 없으면 통과한다`() {
        request.method = "POST"

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }

    @Test
    fun `GET 요청은 멱등성 체크를 건너뛴다`() {
        request.method = "GET"
        request.addHeader("Idempotency-Key", "abc-123")

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }

    @Test
    fun `새로운 키로 요청 시 락을 획득하고 진행한다`() {
        request.method = "POST"
        request.addHeader("Idempotency-Key", "abc-123")

        whenever(idempotencyService.tryAcquire("abc-123")).thenReturn(true)

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }

    @Test
    fun `이미 처리된 키로 요청 시 캐시된 응답을 반환한다`() {
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
    fun `처리 중인 키로 요청 시 409를 반환한다`() {
        request.method = "POST"
        request.addHeader("Idempotency-Key", "abc-123")

        whenever(idempotencyService.tryAcquire("abc-123")).thenReturn(false)
        whenever(idempotencyService.getResponse("abc-123")).thenReturn("PROCESSING")

        val result = interceptor.preHandle(request, response, Any())

        assertFalse(result)
        assertEquals(409, response.status)
    }
}
