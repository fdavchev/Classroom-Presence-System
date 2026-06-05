import uvicorn

if __name__ == "__main__":
    uvicorn.run(
        "main:app",          # ← flat: main.py lives directly in backend/
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info",
    )
