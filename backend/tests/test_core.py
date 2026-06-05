"""
tests/test_core.py
------------------
Unit tests for critical business logic that can run WITHOUT Firebase.

Strategy: Mock the Firestore client and Firebase Auth so tests are:
  - Fast (no network calls)
  - Deterministic (no external state)
  - CI-friendly (no credentials required)

Run: pytest tests/ -v
"""

import pytest
from unittest.mock import MagicMock, patch
from datetime import datetime, timezone, timedelta

from utils.jwt_handler import create_access_token, decode_access_token
from schemas.contracts import (
    BulkAttendanceRequest, NfcPayload, LoginRequest
)


# ════════════════════════════════════════════════════════════════════════════
# JWT Handler Tests
# ════════════════════════════════════════════════════════════════════════════

class TestJwtHandler:

    def test_create_and_decode_round_trip(self):
        """Token issued → decoded → claims match."""
        token = create_access_token(
            uid="uid_123",
            email="teacher@school.edu",
            role="teacher",
        )
        payload = decode_access_token(token)
        assert payload["sub"] == "uid_123"
        assert payload["email"] == "teacher@school.edu"
        assert payload["role"] == "teacher"

    def test_expired_token_raises(self):
        """A token with expires_in=-1 should be immediately expired."""
        import jwt

        token = create_access_token(
            uid="uid_exp",
            email="x@x.com",
            role="student",
            expires_in=-1,   # already expired
        )
        with pytest.raises(jwt.ExpiredSignatureError):
            decode_access_token(token)

    def test_tampered_token_raises(self):
        """Mutating the token body should fail signature verification."""
        import jwt

        token = create_access_token("u1", "a@b.com", "admin")
        tampered = token[:-4] + "AAAA"   # corrupt last 4 chars
        with pytest.raises(jwt.InvalidTokenError):
            decode_access_token(tampered)


# ════════════════════════════════════════════════════════════════════════════
# Schema Validation Tests
# ════════════════════════════════════════════════════════════════════════════

class TestSchemaValidation:

    def test_nfc_payload_rejects_whitespace_student_id(self):
        """student_id must not contain spaces — prevents injection attacks."""
        from pydantic import ValidationError

        with pytest.raises(ValidationError):
            NfcPayload(
                student_id="STU 001",      # space in ID
                student_name="Jane Doe",
                auth_token="firebase_uid",
            )

    def test_nfc_payload_valid(self):
        payload = NfcPayload(
            student_id="STU-2024-001",
            student_name="Jane Doe",
            course_enrolled="Software Engineering",
            auth_token="firebase_uid_abc123",
        )
        assert payload.student_id == "STU-2024-001"

    def test_bulk_request_rejects_empty_records(self):
        from pydantic import ValidationError

        with pytest.raises(ValidationError):
            BulkAttendanceRequest(session_id="sess_1", records=[])


# ════════════════════════════════════════════════════════════════════════════
# Attendance Service Tests (Firestore mocked)
# ════════════════════════════════════════════════════════════════════════════

class TestAttendanceServiceDuplication:

    def _build_service(self, existing_docs=None, student_is_valid=True):
        """Build an AttendanceService with a fully mocked Firestore client."""
        from services.attendance_service import AttendanceService

        mock_db = MagicMock()

        # Mock: dedup check query
        mock_dedup_query = MagicMock()
        mock_dedup_query.where.return_value = mock_dedup_query
        mock_dedup_query.limit.return_value = mock_dedup_query
        # If existing_docs provided, simulate a duplicate found
        mock_dedup_query.get.return_value = existing_docs or []

        # Mock: users collection for student validation
        mock_user_doc = MagicMock()
        mock_user_doc.exists = student_is_valid
        mock_user_doc.to_dict.return_value = {"role": "student"} if student_is_valid else {}
        mock_db.collection.return_value.document.return_value.get.return_value = mock_user_doc

        # Chain: collection().where().limit().get() for dedup
        mock_db.collection.return_value.where.return_value = mock_dedup_query

        # Mock batch
        mock_batch = MagicMock()
        mock_db.batch.return_value = mock_batch

        service = AttendanceService.__new__(AttendanceService)
        service._db = mock_db
        return service, mock_batch

    def test_duplicate_record_is_skipped(self):
        """If dedup_key already exists in Firestore, record is counted as duplicate."""
        service, mock_batch = self._build_service(
            existing_docs=[MagicMock()]  # simulate existing record
        )
        request = BulkAttendanceRequest(
            session_id="sess_1",
            records=[
                NfcPayload(
                    student_id="STU-001",
                    student_name="Alice",
                    auth_token="uid_alice",
                )
            ],
        )
        result = service.ingest_bulk(request, teacher_uid="teacher_uid")
        assert result.duplicates_skipped == 1
        assert result.accepted == 0
        mock_batch.commit.assert_not_called()

    def test_invalid_student_uid_produces_error(self):
        """If the student's auth_token doesn't map to a student, record is rejected."""
        service, mock_batch = self._build_service(
            existing_docs=[],
            student_is_valid=False,
        )
        request = BulkAttendanceRequest(
            session_id="sess_2",
            records=[
                NfcPayload(
                    student_id="STU-999",
                    student_name="Fake Student",
                    auth_token="uid_not_a_student",
                )
            ],
        )
        result = service.ingest_bulk(request, teacher_uid="teacher_uid")
        assert result.accepted == 0
        assert len(result.errors) == 1


# ════════════════════════════════════════════════════════════════════════════
# Statistics Service Tests (Firestore mocked)
# ════════════════════════════════════════════════════════════════════════════

class TestStatisticsAggregation:

    def _build_service_with_records(self, records):
        """Build StatisticsService with pre-loaded mock Firestore records."""
        from services.statistics_service import StatisticsService

        mock_db = MagicMock()
        mock_query = MagicMock()
        mock_query.where.return_value = mock_query

        mock_docs = []
        for rec in records:
            doc = MagicMock()
            doc.to_dict.return_value = rec
            mock_docs.append(doc)

        mock_query.stream.return_value = iter(mock_docs)
        mock_db.collection.return_value.where.return_value = mock_query

        service = StatisticsService.__new__(StatisticsService)
        service._db = mock_db
        return service

    def test_today_count_is_correct(self):
        now = datetime.now(timezone.utc)
        records = [
            {
                "student_id": "s1",
                "course_enrolled": "Math",
                "teacher_id": "t1",
                "timestamp": now - timedelta(hours=1),       # today
            },
            {
                "student_id": "s2",
                "course_enrolled": "Math",
                "teacher_id": "t1",
                "timestamp": now - timedelta(days=5),        # earlier this week
            },
        ]
        service = self._build_service_with_records(records)
        result = service.get_statistics("t1", "teacher")
        assert result.total_records_today == 1
        assert result.total_records_week >= 1

    def test_daily_trend_has_30_entries(self):
        service = self._build_service_with_records([])
        result = service.get_statistics("admin_uid", "admin")
        assert len(result.daily_trend) == 30

    def test_per_course_aggregation(self):
        now = datetime.now(timezone.utc)
        records = [
            {"student_id": "s1", "course_enrolled": "CS101", "teacher_id": "t1", "timestamp": now},
            {"student_id": "s2", "course_enrolled": "CS101", "teacher_id": "t1", "timestamp": now},
            {"student_id": "s3", "course_enrolled": "Math",  "teacher_id": "t1", "timestamp": now},
        ]
        service = self._build_service_with_records(records)
        result = service.get_statistics("t1", "teacher")

        cs101 = next(c for c in result.per_course if c.course == "CS101")
        assert cs101.total_records == 2
        assert cs101.unique_students == 2
