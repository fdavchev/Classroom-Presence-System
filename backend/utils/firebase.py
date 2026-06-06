"""
utils/firebase.py — Firebase Admin SDK singleton.
initialize_firebase() is called once in main.py lifespan.
All services call get_firestore() to get the client.
"""

import os
from pathlib import Path
import firebase_admin
from firebase_admin import credentials, firestore, auth as firebase_auth

_initialized = False
_db = None


def initialize_firebase() -> None:
    global _initialized, _db
    if _initialized:
        return

    # 1. Grab env variable if it exists
    env_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_KEY")
    
    if env_path:
        key_path = env_path
    else:
        # 2. DYNAMIC FALLBACK PATH: Anchors directly to the backend/ folder
        # __file__ is classroom-presence-system/backend/utils/firebase.py
        # .parent is utils/ -> .parent is backend/
        backend_dir = Path(__file__).resolve().parent.parent
        key_path = str(backend_dir / "serviceAccountKey.json")

    # 3. Check file existence cleanly
    if not os.path.exists(key_path):
        raise FileNotFoundError(
            f"serviceAccountKey.json not found at '{key_path}'. "
            "Place it in the backend/ folder."
        )

    cred = credentials.Certificate(key_path)
    firebase_admin.initialize_app(cred)
    _db = firestore.client()
    _initialized = True


def get_firestore():
    if _db is None:
        raise RuntimeError("Firebase not initialized. This is a startup bug.")
    return _db


def get_firebase_auth():
    return firebase_auth