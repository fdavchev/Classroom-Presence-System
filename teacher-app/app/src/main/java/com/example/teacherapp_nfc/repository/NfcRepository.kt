package com.example.teacherapp_nfc.repository

import com.example.teacherapp_nfc.data.AttendanceDao
import com.example.teacherapp_nfc.data.AttendanceRecord
import com.example.teacherapp_nfc.data.AttendanceSession
import com.example.teacherapp_nfc.network.ApiClient
import com.example.teacherapp_nfc.network.BulkAttendanceRequest
import com.example.teacherapp_nfc.network.LoginRequest
import com.example.teacherapp_nfc.network.LoginResponse
import com.example.teacherapp_nfc.network.NfcApiService
import com.example.teacherapp_nfc.network.NfcPayloadDto

class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val nfcApiService: NfcApiService
) {
    // ── Login ────────────────────────────────────────────────────────────────
    // Calls POST /api/login with the teacher's email and password.
    // If it works, saves the token into ApiClient so future requests are auth'd.
    // Returns the LoginResponse on success, or null if something went wrong.
    suspend fun loginTeacher(firebaseToken: String): LoginResponse? {
    val response = runCatching {
        nfcApiService.login(LoginRequest(firebase_id_token = firebaseToken))
    }.getOrNull()

    return if (response?.isSuccessful == true) {
        val body = response.body()
        if (body != null) {
            ApiClient.setToken(body.server_token)
        }
        body
    } else {
        null
    }
}

    // ── Session management ───────────────────────────────────────────────────
    suspend fun startSession(teacherId: String, className: String): AttendanceSession {
        val session = AttendanceSession(
            teacherId = teacherId,
            className = className,
            startedAt = System.currentTimeMillis()
        )
        val id = attendanceDao.insertSession(session)
        return session.copy(id = id)
    }

    suspend fun endSession(session: AttendanceSession): AttendanceSession {
        val endedAt = System.currentTimeMillis()
        attendanceDao.endSession(session.id, endedAt)
        ApiClient.clearToken() // remove the badge when session ends
        return session.copy(active = false, endedAt = endedAt)
    }

    // ── Save one NFC record locally ──────────────────────────────────────────
    suspend fun saveRecord(record: AttendanceRecord): AttendanceRecord {
        val id = attendanceDao.insertRecord(record)
        return record.copy(id = id)
    }

    // ── Sync all unsynced records to backend ─────────────────────────────────
    // Grabs every record that hasn't been synced yet (synced = false in the DB).
    // Bundles them into one bulk request and sends it to POST /api/attendance.
    // If the backend accepts them, marks each one as synced in the local DB.
    suspend fun syncPendingRecords(session: AttendanceSession?): Int {
        val s = session ?: return 0
        val pending = attendanceDao.getPendingRecords()
        if (pending.isEmpty()) return 0

        // Convert our local records into the shape the backend expects.
        // The "auth_token" field carries the student's Firebase UID,
        // which we stored in the rawPayload field when we received the NFC tap.
        val payloads = pending.map { record ->
            NfcPayloadDto(
                student_id = record.studentId,
                student_name = record.studentName,
                course_enrolled = record.className.ifBlank { null },
                auth_token = record.rawPayload  // rawPayload holds the student UID
            )
        }

        val request = BulkAttendanceRequest(
            session_id = s.id.toString(), // backend wants a String, not a number
            records = payloads
        )

        val response = runCatching {
            nfcApiService.uploadAttendance(request)
        }.getOrNull()

        return if (response?.isSuccessful == true) {
            // Mark every record as synced in the local database.
            pending.forEach { attendanceDao.markSynced(it.id) }
            response.body()?.accepted ?: pending.size
        } else {
            0
        }
    }
}