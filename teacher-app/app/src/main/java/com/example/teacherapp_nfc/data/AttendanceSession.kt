package com.example.teacherapp_nfc.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_sessions")
data class AttendanceSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val teacherId: String,
    val className: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val active: Boolean = true
)
