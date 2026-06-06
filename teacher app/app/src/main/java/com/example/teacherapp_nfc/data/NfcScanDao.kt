package com.example.teacherapp_nfc.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttendanceDao {
    @Insert
    suspend fun insertRecord(record: AttendanceRecord): Long

    @Insert
    suspend fun insertSession(session: AttendanceSession): Long

    @Query("SELECT * FROM attendance_sessions WHERE active = 1 LIMIT 1")
    suspend fun getActiveSession(): AttendanceSession?

    @Query("UPDATE attendance_sessions SET active = 0, endedAt = :endedAt WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAt: Long)

    @Query("SELECT * FROM attendance_records WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingRecords(): List<AttendanceRecord>

    @Query("UPDATE attendance_records SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT * FROM attendance_records WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getRecordsForSession(sessionId: Long): List<AttendanceRecord>
}
