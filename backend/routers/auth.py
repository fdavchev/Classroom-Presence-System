"""
routers/auth.py — POST /api/login
"""

from fastapi import APIRouter, HTTPException, status
from schemas.contracts import LoginRequest, LoginResponse
from services.auth_service import AuthService, AuthenticationError

router = APIRouter()


@router.post("/login", response_model=LoginResponse, status_code=200)
async def login(request: LoginRequest) -> LoginResponse:
    """
    Verify a Firebase ID token and return a CPS server JWT.
    Used by Teacher App, Student App, and Web Dashboard.
    """
    try:
        # ✅ Service created INSIDE the function — Firebase is guaranteed ready
        return AuthService().login(request)
    except AuthenticationError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(exc))
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Server error during login: {exc}",
        )
