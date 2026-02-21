package com.bankcore

import com.bankcore.interest.service.DailyInterestAccrualScheduler
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class BankCoreApplication {
    @Bean
    @ConditionalOnProperty(name = ["interest.batch.run-once"], havingValue = "true")
    fun runOnceInterestBatchRunner(
        scheduler: DailyInterestAccrualScheduler
    ): ApplicationRunner {
        return ApplicationRunner {
            scheduler.runDailyAccrualOnce()
            System.exit(0)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<BankCoreApplication>(*args)
}
