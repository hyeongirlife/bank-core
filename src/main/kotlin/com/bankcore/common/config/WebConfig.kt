package com.bankcore.common.config

import com.bankcore.common.idempotency.IdempotencyInterceptor
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.util.ContentCachingResponseWrapper

@Configuration
class WebConfig(
    private val idempotencyInterceptor: IdempotencyInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(idempotencyInterceptor)
            .addPathPatterns("/api/**")
    }

    @Bean
    fun contentCachingFilter(): FilterRegistrationBean<ContentCachingFilter> {
        return FilterRegistrationBean(ContentCachingFilter()).apply {
            addUrlPatterns("/api/*")
            order = 1
        }
    }
}

class ContentCachingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrapper = ContentCachingResponseWrapper(response)
        filterChain.doFilter(request, wrapper)
        wrapper.copyBodyToResponse()
    }
}
