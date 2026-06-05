"""
utils/jwt_handler.py — CPS server JWT (HS256).
"""

import os
import time
import jwt  # PyJWT

SECRET_KEY: str = os.getenv("CPS_JWT_SECRET", "CHANGE_ME_USE_A_LONG_RANDOM_SECRET")
ALGORITHM: str = "HS256"
EXPIRE_SECONDS: int = int(os.getenv("JWT_EXPIRE_SECONDS", str(3600 * 8)))  # 8 hours


def create_access_token(uid: str, email: str, role: str) -> str:
    now = int(time.time())
    payload = {
        "sub": uid,
        "email": email,
        "role": role,
        "iat": now,
        "exp": now + EXPIRE_SECONDS,
    }
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)


def decode_access_token(token: str) -> dict:
    """Raises jwt.ExpiredSignatureError or jwt.InvalidTokenError on failure."""
    return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
