"""
services/attendance_service.py — Attendance ingestion and retrieval.

Key behaviours:
- Idempotent bulk write: dedup_key = "{session_id}::{student_id}"
- Student UID validation before write (prevents forged NFC payloads)
- RBAC-scoped queries: teacher sees own records, admin sees all
- Offset pagination for dashboard
"""

from datetime import datetime, timezone, timedelta
from typing import List, Optional, Tuple

from utils.firebase import get_firestore
from models.firestore_schema import Collections, AttendanceFields, UserFields, UserRoles
from schemas.contracts import (
    BulkAttendanceRequest, BulkAttendanceResponse,
    AttendanceEntry, AttendanceListResponse, PaginationMeta, NfcPayload,
)


class AttendanceService:

    def ingest_bulk(
        self,
        request: BulkAttendanceRequest,
        teacher_uid: str,
    ) -> BulkAttendanceResponse:
        db = get_firestore()
        accepted = 0
        duplicates = 0
        errors: List[str] = []

        # Firestore batch limit = 500; chunk to be safe
        chunk_size = 490
        for i in range(0, len(request.records), chunk_size):
            chunk = request.records[i: i + chunk_size]
            a, d, e = self._write_chunk(db, chunk, request.session_id, teacher_uid)
            accepted += a
            duplicates += d
            errors.extend(e)

        return BulkAttendanceResponse(
            session_id=request.session_id,
            submitted=len(request.records),
            accepted=accepted,
            duplicates_skipped=duplicates,
            errors=errors,
        )

    def _write_chunk(
        self, db, records: List[NfcPayload], session_id: str, teacher_uid: str
    ) -> Tuple[int, int, List[str]]:
        accepted = 0
        duplicates = 0
        errors: List[str] = []
        batch = db.batch()
        has_writes = False

        for record in records:
            dedup_key = f"{session_id}::{record.student_id}"

            # Duplicate check
            existing = (
                db.collection(Collections.ATTENDANCE)
                .where(AttendanceFields.DEDUP_KEY, "==", dedup_key)
                .limit(1)
                .get()
            )
            if existing:
                duplicates += 1
                continue

            # Validate student UID
            if not self._is_valid_student(db, record.auth_token):
                errors.append(
                    f"student_id={record.student_id}: auth_token not a registered student."
                )
                continue

            doc_ref = db.collection(Collections.ATTENDANCE).document()
            batch.set(doc_ref, {
                AttendanceFields.RECORD_ID: doc_ref.id,
                AttendanceFields.STUDENT_ID: record.student_id,
                AttendanceFields.STUDENT_NAME: record.student_name,
                AttendanceFields.COURSE_ENROLLED: record.course_enrolled,
                AttendanceFields.TEACHER_ID: teacher_uid,
                AttendanceFields.SESSION_ID: session_id,
                AttendanceFields.TIMESTAMP: datetime.now(timezone.utc),
                AttendanceFields.SYNC_SOURCE: "teacher_app",
                AttendanceFields.DEDUP_KEY: dedup_key,
            })
            has_writes = True
            accepted += 1

        if has_writes:
            batch.commit()

        return accepted, duplicates, errors

    def _is_valid_student(self, db, firebase_uid: str) -> bool:
        try:
            doc = db.collection(Collections.USERS).document(firebase_uid).get()
            return doc.exists and doc.to_dict().get(UserFields.ROLE) == UserRoles.STUDENT
        except Exception:
            return False

    def get_records(
        self,
        requester_uid: str,
        requester_role: str,
        page: int = 1,
        page_size: int = 50,
        filter_date: Optional[str] = None,
        filter_course: Optional[str] = None,
        filter_student_id: Optional[str] = None,
    ) -> AttendanceListResponse:
        from google.cloud import firestore as fs

        db = get_firestore()
        col = db.collection(Collections.ATTENDANCE)
        query = col.order_by(AttendanceFields.TIMESTAMP, direction=fs.Query.DESCENDING)

        # RBAC
        if requester_role == UserRoles.TEACHER:
            query = query.where(AttendanceFields.TEACHER_ID, "==", requester_uid)
        elif requester_role == UserRoles.STUDENT:
            query = query.where(AttendanceFields.STUDENT_ID, "==", requester_uid)

        # Optional filters
        if filter_course:
            query = query.where(AttendanceFields.COURSE_ENROLLED, "==", filter_course)
        if filter_student_id and requester_role != UserRoles.STUDENT:
            query = query.where(AttendanceFields.STUDENT_ID, "==", filter_student_id)
        if filter_date:
            try:
                day_start = datetime.fromisoformat(filter_date).replace(tzinfo=timezone.utc)
                day_end = day_start + timedelta(days=1)
                query = query.where(AttendanceFields.TIMESTAMP, ">=", day_start)
                query = query.where(AttendanceFields.TIMESTAMP, "<", day_end)
            except ValueError:
                pass

        all_docs = list(query.stream())
        total = len(all_docs)
        page_docs = all_docs[(page - 1) * page_size: page * page_size]

        return AttendanceListResponse(
            data=[self._to_entry(d) for d in page_docs],
            meta=PaginationMeta(
                total=total,
                page=page,
                page_size=page_size,
                total_pages=max(1, -(-total // page_size)),
            ),
        )

    @staticmethod
    def _to_entry(doc) -> AttendanceEntry:
        d = doc.to_dict()
        return AttendanceEntry(
            record_id=d.get(AttendanceFields.RECORD_ID, doc.id),
            student_id=d[AttendanceFields.STUDENT_ID],
            student_name=d[AttendanceFields.STUDENT_NAME],
            course_enrolled=d.get(AttendanceFields.COURSE_ENROLLED),
            teacher_id=d[AttendanceFields.TEACHER_ID],
            session_id=d[AttendanceFields.SESSION_ID],
            timestamp=d[AttendanceFields.TIMESTAMP],
            sync_source=d.get(AttendanceFields.SYNC_SOURCE, "teacher_app"),
        )
