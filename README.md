# AmzStore - Spring Boot & OpenObserve Monitoring Assignment

This repository contains the complete solution for the assignment: **"Monitoring SpringBoot Applications: Logs and Traces with OpenObserve"**.

## 📁 Repository Contents

* `docker-compose.yml`: Local OpenObserve container deployment via Docker Desktop.
* `run-all.ps1`: One-click PowerShell script to start Docker, Spring Boot backend, and React frontend.
* `BLOG_POST.md`: Comprehensive, publication-ready technical blog post submitted for the assignment.
* `VIDEO_TUTORIAL_GUIDE.md`: Step-by-step 5-10 minute script and walkthrough guide for recording your video presentation.
* `/backend`: Spring Boot 3 Java application (`AmzStore`) instrumented with OpenTelemetry OTLP logging and distributed tracing.
* `/frontend`: React + Vite web application with interactive product catalog, shopping cart, load simulator, and trace ID inspector.

---

## 🚀 Quick Start Guide

### 1. Launch OpenObserve with Docker Desktop
```bash
docker-compose up -d
```
Access OpenObserve at `http://localhost:5080` (Credentials: `root@example.com` / `ComplexPassword123`).

### 2. Start Spring Boot Backend (Port 8080)
```bash
cd backend
mvn spring-boot:run
```

### 3. Start React Frontend (Port 3000)
```bash
cd frontend
npm install
npm run dev
```

Or simply run `.\run-all.ps1` in PowerShell!

---

## 📄 Deliverables Summary

1. **Blog Post**: See [BLOG_POST.md](BLOG_POST.md)
2. **Video Guide**: See [VIDEO_TUTORIAL_GUIDE.md](VIDEO_TUTORIAL_GUIDE.md)
