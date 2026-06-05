"""
models/firestore_schema.py — Firestore collection and field name constants.
Single source of truth — change a name here and it updates everywhere.
"""


class Collections:
    USERS = "users"
    SESSIONS = "sessions"
    ATTENDANCE = "attendance_records"


class UserFields:
    UID = "uid"
    EMAIL = "email"
    ROLE = "role"
    DISPLAY_NAME = "display_name"
    ENROLLED_COURSES = "enrolled_courses"
    CREATED_AT = "created_at"


class AttendanceFields:
    RECORD_ID = "record_id"
    STUDENT_ID = "student_id"
    STUDENT_NAME = "student_name"
    COURSE_ENROLLED = "course_enrolled"
    TEACHER_ID = "teacher_id"
    SESSION_ID = "session_id"
    TIMESTAMP = "timestamp"
    SYNC_SOURCE = "sync_source"
    DEDUP_KEY = "dedup_key"   # "{session_id}::{student_id}" — prevents duplicates


class UserRoles:
    TEACHER = "teacher"
    STUDENT = "student"
    ADMIN = "admin"
    ALL = {"teacher", "student", "admin"}
