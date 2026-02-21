package com.bankcore.db.migration

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class V7MigrationSqlTest {

    @Test
    fun `V7 마이그레이션에 중도해지 정산 스키마가 포함되어 있다`() {
        val migrationSql = this::class.java.classLoader
            .getResource("db/migration/V7__add_early_termination_settlement.sql")
            ?.readText()

        assertNotNull(migrationSql)
        assertTrue(migrationSql!!.contains("ALTER TABLE account"))
        assertTrue(migrationSql.contains("maturity_date DATE NULL"))
        assertTrue(migrationSql.contains("INTEREST_SETTLEMENT"))
        assertTrue(migrationSql.contains("CREATE TABLE interest_settlement"))
        assertTrue(migrationSql.contains("settlement_type"))
        assertTrue(migrationSql.contains("UNIQUE KEY uk_interest_settlement_account_type (account_id, settlement_type)"))
    }
}
