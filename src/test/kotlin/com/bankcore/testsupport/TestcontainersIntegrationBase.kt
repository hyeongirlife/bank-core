package com.bankcore.testsupport

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class TestcontainersIntegrationBase {

    companion object {
        private val redisContainer = GenericContainer(DockerImageName.parse("redis:7.2.5")).apply {
            withExposedPorts(6379)
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerRedisProperties(registry: DynamicPropertyRegistry) {
            registry.add("TEST_REDIS_HOST") { redisContainer.host }
            registry.add("TEST_REDIS_PORT") { redisContainer.getMappedPort(6379) }
        }
    }
}
