package com.example.platinumfinance.models

data class RegisterRequest(
    val saIdNumber: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val message: String,
    val userId: String?,
    val firstName: String?,
    val creditTier: String?
)

/**
 * Data Transfer Object matching C# LoanApplicationDto in LoansController.cs
 * Used for Part 1 Core Loan Calculation & Offline-to-Online API Synchronisation.
 */
data class LoanApplicationRequest(
    val userId: String,
    val principalAmount: Double,
    val initiationFee: Double,
    val serviceFee: Double,
    val interestRate: Double,
    val totalRepayment: Double
)

data class LoanApplicationResponse(
    val message: String,
    val loanId: Any?
)