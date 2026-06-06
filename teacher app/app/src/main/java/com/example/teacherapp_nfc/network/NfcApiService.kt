package com.example.teacherapp_nfc.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface NfcApiService {
    @POST("attendance-records")
    suspend fun uploadAttendance(@Body request: AttendanceUploadRequest): Response<Unit>
}

data class AttendanceUploadRequest(
    val sessionId: Long,
    val teacherId: String,
    val className: String,
    val studentId: String,
    val studentName: String,
    val timestamp: Long,
    val rawPayload: String
)
