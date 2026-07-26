# Stop Script for AmzStore & OpenObserve Stack
Write-Host "=======================================================================" -ForegroundColor Yellow
Write-Host "    Stopping OpenObserve Multi-Microservice Stack" -ForegroundColor Yellow
Write-Host "=======================================================================" -ForegroundColor Yellow

# 1. Stop Java Spring Boot Microservices
Write-Host "`n[1/3] Stopping Java Spring Boot Microservices..." -ForegroundColor Gray
Stop-Process -Name java -Force -ErrorAction SilentlyContinue

# 2. Stop Node / Vite Frontend processes
Write-Host "[2/3] Stopping React Node processes..." -ForegroundColor Gray
Stop-Process -Name node -Force -ErrorAction SilentlyContinue

# 3. Stop OpenObserve Docker Container
Write-Host "[3/3] Stopping OpenObserve Docker Container..." -ForegroundColor Gray
docker-compose down

Write-Host "`n=======================================================================" -ForegroundColor Green
Write-Host "  STACK STOPPED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "=======================================================================" -ForegroundColor Green
