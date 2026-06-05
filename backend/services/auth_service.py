"""
services/auth_service.py — Authentication business logic.

Flow:
  1. Verify Firebase ID token (proves the user logged in on the client).
  2. Fetch or create their Firestore profile (gets their role).
  3. Issue a CPS server JWT (used for all subsequent API calls).
"""

from datetime import datetime, timezone

from utils.firebase import get_firestore, get_firebase_auth
from utils.jwt_handler import create_access_token
from models.firestore_schema import Collections, UserFields, UserRoles
from schemas.contracts import LoginRequest, LoginResponse


class AuthenticationError(Exception):
    pass


class AuthService:
    """Instantiated fresh per-request inside the router — never at module level."""

    def login(self, request: LoginRequest) -> LoginResponse:
        db = get_firestore()
        fb_auth = get_firebase_auth()

        # 1. Verify Firebase token
        try:
            decoded = fb_auth.verify_id_token(request.firebase_id_token, check_revoked=True)
        except fb_auth.RevokedIdTokenError:
            raise AuthenticationError("Token revoked. Please log in again.")
        except fb_auth.ExpiredIdTokenError:
            raise AuthenticationError("Firebase token expired.")
        except Exception as e:
            raise AuthenticationError(f"Invalid Firebase token: {e}")

        uid: str = decoded["uid"]
        email: str = decoded.get("email", "")

        # 2. Fetch or create Firestore profile
        doc_ref = db.collection(Collections.USERS).document(uid)
        doc = doc_ref.get()

        if doc.exists:
            profile = doc.to_dict()
        else:
            profile = {
                UserFields.UID: uid,
                UserFields.EMAIL: email,
                UserFields.ROLE: UserRoles.STUDENT,  # default; promote via console
                UserFields.DISPLAY_NAME: None,
                UserFields.ENROLLED_COURSES: [],
                UserFields.CREATED_AT: datetime.now(timezone.utc),
            }
            doc_ref.set(profile)

        role = profile.get(UserFields.ROLE, UserRoles.STUDENT)

        # 3. Issue CPS JWT
        server_token = create_access_token(uid=uid, email=email, role=role)

        return LoginResponse(
            user_id=uid,
            email=email,
            role=role,
            display_name=profile.get(UserFields.DISPLAY_NAME),
            server_token=server_token,
        )
