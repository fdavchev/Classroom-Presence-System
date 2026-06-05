"""
Classroom Presence System (CPS) — FastAPI Backend
All files sit directly inside backend/ — no app/ subfolder.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from utils.firebase import initialize_firebase
from routers import auth, attendance, statistics


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Firebase is initialized FIRST, before any request can arrive."""
    initialize_firebase()
    print("✅ Firebase Admin SDK initialized.")
    yield
    print("🔴 Server shutting down.")


app = FastAPI(
    title="Classroom Presence System API",
    version="1.0.0",
    lifespan=lifespan,
)

# ── CORS ──────────────────────────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ───────────────────────────────────────────────────────────────
app.include_router(auth.router,       prefix="/api", tags=["Authentication"])
app.include_router(attendance.router, prefix="/api", tags=["Attendance"])
app.include_router(statistics.router, prefix="/api", tags=["Statistics"])


@app.get("/", tags=["Health"])
async def health():
    return {"status": "online", "service": "CPS Backend v1.0.0", "docs": "/docs"}
