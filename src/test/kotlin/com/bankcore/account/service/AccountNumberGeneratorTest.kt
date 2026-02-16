package com.bankcore.account.service

import com.bankcore.account.repository.AccountRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AccountNumberGeneratorTest {

    private val accountRepository = mock<AccountRepository>()
    private val generator = AccountNumberGenerator(accountRepository)

    @Test
    fun `should generate account number in 110-XXX-XXXXXX format`() {
        whenever(accountRepository.existsByAccountNumber(org.mockito.kotlin.any())).thenReturn(false)
        
        val accountNumber = generator.generate()
        val regex = Regex("^110-\\d{3}-\\d{6}$")
        assertTrue(accountNumber.matches(regex), "Generated: $accountNumber")
    }

    @Test
    fun `should generate unique account numbers`() {
        whenever(accountRepository.existsByAccountNumber(org.mockito.kotlin.any())).thenReturn(false)
        
        val numbers = (1..100).map { generator.generate() }.toSet()
        assertEquals(100, numbers.size)
    }

    @Test
    fun `should regenerate when account number already exists`() {
        val existingNumber = "110-123-456789"
        val newNumber = "110-789-123456"
        
        whenever(accountRepository.existsByAccountNumber(existingNumber)).thenReturn(true)
        whenever(accountRepository.existsByAccountNumber(newNumber)).thenReturn(false)
        
        // Mock the generator to return specific values
        val generatorWithMock = object : AccountNumberGenerator(accountRepository) {
            private var callCount = 0
            override fun generateRandom(): String {
                return if (callCount++ == 0) existingNumber else newNumber
            }
        }
        
        val result = generatorWithMock.generate()
        assertEquals(newNumber, result)
    }

    @Test
    fun `should throw exception when max retry attempts exceeded`() {
        whenever(accountRepository.existsByAccountNumber(org.mockito.kotlin.any())).thenReturn(true)
        
        assertThrows<IllegalStateException> {
            generator.generate()
        }
    }
}
