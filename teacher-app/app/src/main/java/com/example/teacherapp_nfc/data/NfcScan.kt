package com.example.teacherapp_nfc.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val teacherId: String,
    val className: String,
    val studentId: String,
    val studentName: String,
    val timestamp: Long,
    val rawPayload: String,
    val synced: Boolean = false
)
