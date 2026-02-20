package com.bankcore.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    companion object {
        private const val API_TITLE = "Bank Core API"
        private const val API_VERSION = "v1"
        private const val API_DESCRIPTION = "계좌 개설/조회/해지 등 수신 도메인 API 문서"
    }

    @Bean
    fun bankCoreOpenApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title(API_TITLE)
                    .version(API_VERSION)
                    .description(API_DESCRIPTION)
            )
    }
}
