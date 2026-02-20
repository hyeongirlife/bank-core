package com.bankcore.db.migration

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class V6MigrationSqlTest {

    @Test
    fun `V6 마이그레이션에 interest_log 멱등 유니크 키가 포함되어 있다`() {
        val migrationSql = this::class.java.classLoader
            .getResource("db/migration/V6__create_rate_interest_tables.sql")
            ?.readText()

        assertNotNull(migrationSql)
        assertTrue(migrationSql!!.contains("CREATE TABLE interest_log"))
        assertTrue(migrationSql.contains("UNIQUE KEY uk_interest_log_account_business_date (account_id, business_date)"))
    }
}
