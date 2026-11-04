package com.example.platinumfinance.network

import com.example.platinumfinance.models.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/register")
    fun registerUser(@Body request: RegisterRequest): Call<AuthResponse>

    @POST("api/auth/login")
    fun loginUser(@Body request: LoginRequest): Call<AuthResponse>

    // Connects Android Offline Sync & Part 1 Loan Feature to C# LoansController
    @POST("api/loans/apply")
    suspend fun applyForLoan(@Body request: LoanApplicationRequest): Response<LoanApplicationResponse>
}