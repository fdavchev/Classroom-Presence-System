"""
routers/statistics.py — GET /api/statistics
"""

from fastapi import APIRouter, Depends, HTTPException, status

from schemas.contracts import StatisticsResponse
from services.statistics_service import StatisticsService
from middleware.auth_guard import require_auth, TokenPayload

router = APIRouter()


@router.get(
    "/statistics",
    response_model=StatisticsResponse,
    status_code=200,
    summary="Get aggregated attendance statistics (last 30 days)",
)
async def get_statistics(
    user: TokenPayload = Depends(require_auth),
) -> StatisticsResponse:
    try:
        return StatisticsService().get_statistics(
            requester_uid=user.uid,
            requester_role=user.role,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Failed to compute stats: {exc}")
