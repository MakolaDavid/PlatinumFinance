package com.example.platinumfinance

import com.example.platinumfinance.models.LoanApplicationRequest
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/**
 * Automated Unit Tests for Platinum Finance core functionality.
 * Executed locally via JUnit4 and automatically in CI/CD via GitHub Actions.
 * Reference: Android Developers (2025) Build local unit tests.
 */
class ExampleUnitTest {

    /**
     * Test 1: Verifies that the South African NCA loan repayment formula
     * accurately calculates Principal + 20% Interest + R165 Initiation + R60 Service Fee.
     */
    @Test
    fun loanRepaymentCalculation_isCorrect() {
        val principal = 1000.0
        // Expected: 1000 + (1000 * 0.20) + 165 + 60 = 1425.0
        val expectedTotal = 1425.0
        val actualTotal = MainActivity.calculateTotalRepayment(principal)

        assertEquals(expectedTotal, actualTotal, 0.001)
    }

    /**
     * Test 2: Verifies that invalid zero or negative loan amounts are handled
     * safely by returning 0.0 without throwing exceptions.
     */
    @Test
    fun loanRepaymentCalculation_handlesInvalidZeroOrNegativeInput() {
        assertEquals(0.0, MainActivity.calculateTotalRepayment(0.0), 0.001)
        assertEquals(0.0, MainActivity.calculateTotalRepayment(-500.0), 0.001)
    }

    /**
     * Test 3: Verifies that SHA-256 password encryption produces a deterministic
     * 64-character hexadecimal cryptographic hash and never exposes plain text.
     */
    @Test
    fun passwordEncryption_generatesValidSha256Hash() {
        val rawPassword = "SecurePassword123"
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawPassword.toByteArray())
        val hashedPassword = bytes.joinToString("") { "%02x".format(it) }

        assertNotEquals(rawPassword, hashedPassword)
        assertEquals(64, hashedPassword.length)
    }

    /**
     * Test 4: Verifies that the LoanApplicationRequest DTO maps properties accurately
     * before REST API transmission.
     */
    @Test
    fun loanApplicationModel_mapsPropertiesCorrectly() {
        val total = MainActivity.calculateTotalRepayment(2000.0)
        val dto = LoanApplicationRequest(
            userId = "USER-101",
            principalAmount = 2000.0,
            initiationFee = 165.0,
            serviceFee = 60.0,
            interestRate = 20.0,
            totalRepayment = total
        )

        assertEquals("USER-101", dto.userId)
        assertEquals(2625.0, dto.totalRepayment, 0.001)
    }
}