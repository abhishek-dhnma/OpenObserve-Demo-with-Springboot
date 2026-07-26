# AmzStore & OpenObserve Launch Script for Windows PowerShell
Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "    Starting OpenObserve Multi-Microservice E-Commerce Monitoring Stack" -ForegroundColor Cyan
Write-Host "=======================================================================" -ForegroundColor Cyan

# 0. Kill any existing Java/Backend processes to ensure fresh code execution
Write-Host "`n[0/5] Stopping any old Java processes..." -ForegroundColor Gray
Stop-Process -Name java -Force -ErrorAction SilentlyContinue

# 1. Start OpenObserve via Docker Desktop
Write-Host "`n[1/5] Launching OpenObserve Docker Container (Port 5080)..." -ForegroundColor Yellow
docker-compose up -d

Write-Host "Waiting for OpenObserve to initialize..." -ForegroundColor Gray
Start-Sleep -Seconds 3

# 2. Check and Setup Maven
Write-Host "`n[2/5] Preparing Maven..." -ForegroundColor Yellow
$BackendDir = Join-Path $PSScriptRoot "backend"
$MvnPath = Get-Command mvn -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source

if (-not $MvnPath) {
    $LocalMvn = Join-Path $BackendDir "maven\apache-maven-3.9.6\bin\mvn.cmd"
    if (-not (Test-Path $LocalMvn)) {
        Write-Host "Downloading portable Apache Maven..." -ForegroundColor Gray
        New-Item -ItemType Directory -Path "$BackendDir\maven" -Force | Out-Null
        $ZipPath = Join-Path $BackendDir "maven\mvn.zip"
        Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip" -OutFile $ZipPath
        Expand-Archive -Path $ZipPath -DestinationPath "$BackendDir\maven" -Force
        Remove-Item $ZipPath -Force
    }
    $MvnCmd = Join-Path $BackendDir "maven\apache-maven-3.9.6\bin\mvn.cmd"
} else {
    $MvnCmd = "mvn"
}

# 3. Launch Standalone Auxiliary Microservices
$ServicesDir = Join-Path $PSScriptRoot "services"

Write-Host "`n[3/5] Starting Standalone Auxiliary Microservices..." -ForegroundColor Yellow

# Inventory Service (Port 8081)
$InvDir = Join-Path $ServicesDir "inventory-service"
Write-Host "Starting Inventory Service (Port 8081)..." -ForegroundColor Gray
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "& '$MvnCmd' spring-boot:run" -WorkingDirectory "$InvDir"

# Payment Service (Port 8082)
$PayDir = Join-Path $ServicesDir "payment-service"
Write-Host "Starting Payment Service (Port 8082)..." -ForegroundColor Gray
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "& '$MvnCmd' spring-boot:run" -WorkingDirectory "$PayDir"

# Fulfillment Service (Port 8083)
$FulDir = Join-Path $ServicesDir "fulfillment-service"
Write-Host "Starting Fulfillment Service (Port 8083)..." -ForegroundColor Gray
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "& '$MvnCmd' spring-boot:run" -WorkingDirectory "$FulDir"

# 4. Launch Main Order Service (Port 8080)
Write-Host "`n[4/5] Starting Main Order Service Gateway (Port 8080)..." -ForegroundColor Yellow
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "& '$MvnCmd' spring-boot:run" -WorkingDirectory "$BackendDir"

# 5. Start React Frontend (Port 3000)
Write-Host "`n[5/5] Starting React Frontend (Port 3000)..." -ForegroundColor Yellow
$FrontendDir = Join-Path $PSScriptRoot "frontend"

if (-not (Test-Path "$FrontendDir\node_modules")) {
    Write-Host "Installing frontend dependencies..." -ForegroundColor Gray
    Set-Location "$FrontendDir"
    npm install
}

Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "npm run dev" -WorkingDirectory "$FrontendDir"

Write-Host "`n=======================================================================" -ForegroundColor Green
Write-Host "  MULTI-MICROSERVICE STACK STARTED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "  - OpenObserve Dashboard: http://localhost:5080 (root@example.com / ComplexPassword123)" -ForegroundColor Green
Write-Host "  - AmzStore Web App:      http://localhost:3000" -ForegroundColor Green
Write-Host "  - Order Service API:     http://localhost:8080/api/products" -ForegroundColor Green
Write-Host "  - Inventory Service:     http://localhost:8081/api/inventory/reserve" -ForegroundColor Green
Write-Host "  - Payment Service:       http://localhost:8082/api/payment/authorize" -ForegroundColor Green
Write-Host "  - Fulfillment Service:   http://localhost:8083/api/fulfillment/ship" -ForegroundColor Green
Write-Host "=======================================================================" -ForegroundColor Green
