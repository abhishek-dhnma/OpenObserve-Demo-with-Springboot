# Video Tutorial Script & Recording Guide (5–10 Minutes)
## Topic: "Monitoring SpringBoot Applications: Logs and Traces with OpenObserve"

---

## 🎬 Video Overview & Timeline Breakdown

| Time Marker | Section Name | Key Visuals / Screen Actions | Verbal Script & Spoken Highlights |
|---|---|---|---|
| **00:00 - 01:15** | **Introduction & Problem Statement** | Title slide or AmzStore web app homepage | *"Welcome! In this tutorial, we will learn how to monitor Spring Boot applications using OpenObserve, Docker, and OpenTelemetry. We will see how to inspect logs and distributed traces in real-time."* |
| **01:15 - 02:45** | **Docker Desktop & OpenObserve Setup** | Docker Desktop UI + Terminal running `docker-compose up -d` | *"Instead of using cloud accounts, we run OpenObserve locally on port 5080 via Docker Desktop. Let's start the container and log into http://localhost:5080 using our credentials."* |
| **02:45 - 04:30** | **Spring Boot 3 OTLP Code Walkthrough** | VS Code showing `pom.xml`, `application.yml`, and `OrderService.java` | *"Here in `pom.xml`, we added `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp`. Notice how `OrderService` creates child trace spans for inventory checks and payment processing."* |
| **04:30 - 07:15** | **Live Action Demo & Error Injection** | Browser with AmzStore (`http://localhost:3000`) placing orders & triggering errors | *"Now let's place an order on AmzStore. Notice the Toast Inspector showing our generated Trace ID! Next, let's toggle 'Simulate Payment Failure' and click checkout to trigger an ERROR log."* |
| **07:15 - 09:30** | **OpenObserve Dashboard Deep Dive** | OpenObserve UI (`http://localhost:5080`) Logs & Traces tabs | *"Let's paste our Trace ID into OpenObserve's Trace tab. Look at this waterfall chart! We can expand the failed span to see the exact stack trace and correlated log entries."* |
| **09:30 - 10:00** | **Summary & Wrap Up** | Slide / AmzStore homepage summary | *"To wrap up, OpenObserve combined with Spring Boot 3 gives you instant log-trace correlation at zero cost. Thank you for watching!"* |

---

## 📋 Step-by-Step Recording Instructions for You

### Step 1: Pre-Recording Checklist
1. Open **Docker Desktop** and make sure it is running.
2. Open PowerShell in your project directory `c:\Users\abhishek dhiman\Desktop\OpenObserve`.
3. Run `.\run-all.ps1` or start the containers manually:
   - Terminal 1: `docker-compose up -d`
   - Terminal 2: `cd backend; mvn spring-boot:run` (or run in IDE)
   - Terminal 3: `cd frontend; npm run dev`
4. Open the following tabs in your browser:
   - **AmzStore UI**: `http://localhost:3000`
   - **OpenObserve Dashboard**: `http://localhost:5080` (login as `root@example.com` / `ComplexPassword123`)

---

### Step 2: What to Record (Demonstration Steps)

1. **Show OpenObserve Login**:
   - Open `http://localhost:5080`, log in, and point out the clean UI.

2. **Demonstrate AmzStore Purchase**:
   - Go to `http://localhost:3000`.
   - Click "Add to Cart" on *Echo Dot*.
   - Open Cart and click "Complete Order".
   - Highlight the **Trace ID Toast** in the bottom right corner and click **Copy**.

3. **Demonstrate Trace Waterfall in OpenObserve**:
   - Switch to OpenObserve (`http://localhost:5080`).
   - Go to **Traces** tab $\rightarrow$ paste the copied `trace_id`.
   - Show the waterfall diagram: `validateStockAndCart` (120ms) and `processPaymentGateway` (250ms).

4. **Demonstrate Error Log & Trace Correlation**:
   - Back in AmzStore, open Cart, check **Simulate Payment Gateway Failure**, and click **Complete Order**.
   - Copy the new red Trace ID.
   - In OpenObserve, open **Logs** tab and show the red `ERROR` log entry.
   - Show how clicking the trace ID takes you directly to the exception trace!

5. **Demonstrate Load Generator**:
   - Click the blue **Generate Load (6 Requests)** button in AmzStore toolbar to populate live telemetry charts in OpenObserve.

---

## 📌 Video Upload & Submission Checklist

- [ ] Record video (5–10 minutes long).
- [ ] Upload video to YouTube (Unlisted or Public) or Vimeo / Google Drive.
- [ ] Copy the video link and paste it into your assignment submission along with `BLOG_POST.md`.
