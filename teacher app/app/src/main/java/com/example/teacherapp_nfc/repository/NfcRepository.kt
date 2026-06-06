package com.example.teacherapp_nfc.repository

import com.example.teacherapp_nfc.data.AttendanceDao
import com.example.teacherapp_nfc.data.AttendanceRecord
import com.example.teacherapp_nfc.data.AttendanceSession
import com.example.teacherapp_nfc.network.NfcApiService
import com.example.teacherapp_nfc.network.AttendanceUploadRequest

class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val nfcApiService: NfcApiService
) {
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
        return session.copy(active = false, endedAt = endedAt)
    }

    suspend fun saveRecord(record: AttendanceRecord): AttendanceRecord {
        val id = attendanceDao.insertRecord(record)
        return record.copy(id = id)
    }

    suspend fun syncPendingRecords(session: AttendanceSession?): Int {
        val pending = attendanceDao.getPendingRecords()
        var syncedCount = 0

        for (record in pending) {
            val response = runCatching {
                nfcApiService.uploadAttendance(
                    AttendanceUploadRequest(
                        sessionId = record.sessionId,
                        teacherId = record.teacherId,
                        className = record.className,
                        studentId = record.studentId,
                        studentName = record.studentName,
                        timestamp = record.timestamp,
                        rawPayload = record.rawPayload
                    )
                )
            }.getOrNull()

            if (response?.isSuccessful == true) {
                attendanceDao.markSynced(record.id)
                syncedCount++
            }
        }

        return syncedCount
    }
}
