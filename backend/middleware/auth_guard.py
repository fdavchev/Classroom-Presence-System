"""
middleware/auth_guard.py — FastAPI JWT Bearer dependency.

Usage:
    @router.get("/protected")
    async def route(user: TokenPayload = Depends(require_auth)):
        ...

    @router.get("/teacher-only")
    async def route(user: TokenPayload = Depends(require_role("teacher", "admin"))):
        ...
"""

from typing import Callable
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt

from utils.jwt_handler import decode_access_token
from pydantic import BaseModel

_bearer = HTTPBearer(auto_error=True)


class TokenPayload(BaseModel):
    uid: str
    email: str
    role: str


def require_auth(
    credentials: HTTPAuthorizationCredentials = Depends(_bearer),
) -> TokenPayload:
    try:
        payload = decode_access_token(credentials.credentials)
        return TokenPayload(uid=payload["sub"], email=payload["email"], role=payload["role"])
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token expired. Please log in again.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token.",
            headers={"WWW-Authenticate": "Bearer"},
        )


def require_role(*allowed_roles: str) -> Callable:
    def _check(user: TokenPayload = Depends(require_auth)) -> TokenPayload:
        if user.role not in allowed_roles:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Access denied. Required: {list(allowed_roles)}, yours: '{user.role}'.",
            )
        return user
    return _check
