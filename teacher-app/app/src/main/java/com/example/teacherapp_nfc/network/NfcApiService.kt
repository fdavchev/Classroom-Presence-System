package com.example.teacherapp_nfc.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// ─── Step 1: Login ──────────────────────────────────────────────────────────
// This matches POST /api/login in the backend.
// We send the teacher's email + password and get back a token.
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val user_id: String,
    val email: String,
    val role: String,
    val display_name: String?,
    val server_token: String   // ← this is the "badge" we save in ApiClient
)

// ─── Step 2: Attendance sync ─────────────────────────────────────────────────
// One attendance record — matches NfcPayload in contracts.py exactly.
data class NfcPayloadDto(
    val version: String = "1.0",
    val student_id: String,
    val student_name: String,
    val course_enrolled: String?,
    val auth_token: String      // ← the student's Firebase UID (from NFC payload)
)

// The wrapper — matches BulkAttendanceRequest in contracts.py.
// session_id is a String here (backend expects String, not a number).
data class BulkAttendanceRequest(
    val session_id: String,
    val records: List<NfcPayloadDto>
)

// What the backend sends back after we sync.
data class BulkAttendanceResponse(
    val session_id: String,
    val submitted: Int,
    val accepted: Int,
    val duplicates_skipped: Int,
    val errors: List<String>
)

// ─── The actual API interface ────────────────────────────────────────────────
// Retrofit reads this interface and automatically creates the real HTTP calls.
interface NfcApiService {

    // Teacher logs in → gets a server_token back
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // Teacher syncs attendance records to the backend
    @POST("attendance")
    suspend fun uploadAttendance(
        @Body request: BulkAttendanceRequest
    ): Response<BulkAttendanceResponse>
}