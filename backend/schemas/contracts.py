"""
schemas/contracts.py — All Pydantic v2 request/response contracts.
"""

from __future__ import annotations
from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field, field_validator
import re


# ── Shared ─────────────────────────────────────────────────────────────────

class PaginationMeta(BaseModel):
    total: int
    page: int
    page_size: int
    total_pages: int


# ── Auth ───────────────────────────────────────────────────────────────────

class LoginRequest(BaseModel):
    firebase_id_token: str = Field(..., min_length=10)


class LoginResponse(BaseModel):
    user_id: str
    email: str
    role: str
    display_name: Optional[str] = None
    server_token: str


# ── Attendance ─────────────────────────────────────────────────────────────

class NfcPayload(BaseModel):
    version: str = "1.0"
    student_id: str = Field(..., min_length=1, max_length=64)
    student_name: str = Field(..., min_length=1, max_length=128)
    course_enrolled: Optional[str] = Field(None, max_length=128)
    auth_token: str = Field(..., min_length=10)

    @field_validator("student_id")
    @classmethod
    def no_whitespace(cls, v: str) -> str:
        if re.search(r"\s", v):
            raise ValueError("student_id must not contain whitespace.")
        return v.strip()


class BulkAttendanceRequest(BaseModel):
    session_id: str = Field(..., min_length=1)
    records: List[NfcPayload] = Field(..., min_length=1, max_length=500)


class BulkAttendanceResponse(BaseModel):
    session_id: str
    submitted: int
    accepted: int
    duplicates_skipped: int
    errors: List[str] = []


class AttendanceEntry(BaseModel):
    record_id: str
    student_id: str
    student_name: str
    course_enrolled: Optional[str]
    teacher_id: str
    session_id: str
    timestamp: datetime
    sync_source: str


class AttendanceListResponse(BaseModel):
    data: List[AttendanceEntry]
    meta: PaginationMeta


# ── Statistics ─────────────────────────────────────────────────────────────

class CourseAttendanceStat(BaseModel):
    course: str
    total_records: int
    unique_students: int


class DailyTrendPoint(BaseModel):
    date: str
    count: int


class StatisticsResponse(BaseModel):
    total_records_today: int
    total_records_week: int
    per_course: List[CourseAttendanceStat]
    daily_trend: List[DailyTrendPoint]
    top_student_id: Optional[str] = None
