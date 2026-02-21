package com.bankcore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class BankCoreApplication

fun main(args: Array<String>) {
    runApplication<BankCoreApplication>(*args)
}
