# Start the Backend in a new independent window
Write-Host "🚀 Launching FastAPI Backend Core..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "python backend/run.py"

# Start the Frontend Dashboard in the current directory
Write-Host "🚀 Launching Web Dashboard Server on port 5500..." -ForegroundColor Cyan
cd dashboard
python -m http.server 5500