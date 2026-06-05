"""
routers/attendance.py — POST /api/attendance  &  GET /api/attendance
"""

from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status

from schemas.contracts import BulkAttendanceRequest, BulkAttendanceResponse, AttendanceListResponse
from services.attendance_service import AttendanceService
from middleware.auth_guard import require_auth, require_role, TokenPayload

router = APIRouter()


@router.post(
    "/attendance",
    response_model=BulkAttendanceResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="Sync attendance records from Teacher App",
)
async def post_attendance(
    request: BulkAttendanceRequest,
    user: TokenPayload = Depends(require_role("teacher", "admin")),
) -> BulkAttendanceResponse:
    try:
        return AttendanceService().ingest_bulk(request, teacher_uid=user.uid)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Failed to process batch: {exc}")


@router.get(
    "/attendance",
    response_model=AttendanceListResponse,
    status_code=200,
    summary="Retrieve attendance records (paginated + filtered)",
)
async def get_attendance(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=200),
    date: Optional[str] = Query(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$"),
    course: Optional[str] = Query(default=None),
    student_id: Optional[str] = Query(default=None),
    user: TokenPayload = Depends(require_auth),
) -> AttendanceListResponse:
    try:
        return AttendanceService().get_records(
            requester_uid=user.uid,
            requester_role=user.role,
            page=page,
            page_size=page_size,
            filter_date=date,
            filter_course=course,
            filter_student_id=student_id,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Failed to retrieve records: {exc}")
