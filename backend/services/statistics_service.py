"""
services/statistics_service.py — Aggregated analytics for the dashboard.
All aggregation done in-memory after a single Firestore fetch (30-day window).
"""

from collections import defaultdict
from datetime import datetime, timezone, timedelta

from utils.firebase import get_firestore
from models.firestore_schema import Collections, AttendanceFields, UserRoles
from schemas.contracts import (
    StatisticsResponse, CourseAttendanceStat, DailyTrendPoint,
)


class StatisticsService:

    def get_statistics(self, requester_uid: str, requester_role: str) -> StatisticsResponse:
        db = get_firestore()
        now = datetime.now(timezone.utc)
        today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        week_start = today_start - timedelta(days=today_start.weekday())
        thirty_days_ago = today_start - timedelta(days=29)

        query = db.collection(Collections.ATTENDANCE).where(
            AttendanceFields.TIMESTAMP, ">=", thirty_days_ago
        )
        if requester_role == UserRoles.TEACHER:
            query = query.where(AttendanceFields.TEACHER_ID, "==", requester_uid)
        elif requester_role == UserRoles.STUDENT:
            query = query.where(AttendanceFields.STUDENT_ID, "==", requester_uid)

        records = [d.to_dict() for d in query.stream()]

        today_count = 0
        week_count = 0
        course_map = defaultdict(lambda: {"total": 0, "students": set()})
        daily_map = defaultdict(int)
        student_freq = defaultdict(int)

        for rec in records:
            ts: datetime = rec.get(AttendanceFields.TIMESTAMP)
            if ts is None:
                continue
            if ts.tzinfo is None:
                ts = ts.replace(tzinfo=timezone.utc)

            course = rec.get(AttendanceFields.COURSE_ENROLLED) or "Unknown"
            sid = rec.get(AttendanceFields.STUDENT_ID, "")
            date_key = ts.strftime("%Y-%m-%d")

            daily_map[date_key] += 1
            course_map[course]["total"] += 1
            course_map[course]["students"].add(sid)
            student_freq[sid] += 1

            if ts >= today_start:
                today_count += 1
            if ts >= week_start:
                week_count += 1

        per_course = [
            CourseAttendanceStat(
                course=course,
                total_records=data["total"],
                unique_students=len(data["students"]),
            )
            for course, data in sorted(course_map.items(), key=lambda x: -x[1]["total"])
        ]

        daily_trend = [
            DailyTrendPoint(
                date=(today_start - timedelta(days=delta)).strftime("%Y-%m-%d"),
                count=daily_map.get(
                    (today_start - timedelta(days=delta)).strftime("%Y-%m-%d"), 0
                ),
            )
            for delta in range(29, -1, -1)
        ]

        top_student = max(student_freq, key=student_freq.get) if student_freq else None

        return StatisticsResponse(
            total_records_today=today_count,
            total_records_week=week_count,
            per_course=per_course,
            daily_trend=daily_trend,
            top_student_id=top_student,
        )
