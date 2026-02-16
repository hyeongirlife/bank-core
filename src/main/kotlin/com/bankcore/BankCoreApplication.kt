package com.bankcore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BankCoreApplication

fun main(args: Array<String>) {
    runApplication<BankCoreApplication>(*args)
}
