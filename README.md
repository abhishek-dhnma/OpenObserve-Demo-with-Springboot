# OpenObserve E-Commerce Monitoring Demo with Spring Boot 3 & OpenTelemetry

A full-stack, 4-service distributed e-commerce telemetry demonstration featuring an **Amazon-style React frontend**, a **Spring Boot 3 microservices architecture**, and real-time observability powered by **OpenObserve** (Logs, Distributed Traces, and Exception Analysis).

---

## 🌟 Architecture Overview

```text
┌────────────────────────────────────────────────────────┐
│             AmzStore React Frontend (Port 3000)        │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP POST /api/orders/checkout
                            ▼
┌────────────────────────────────────────────────────────┐
│           order-service Gateway (Port 8080)            │
└───────┬───────────────────┬────────────────────┬───────┘
        │ HTTP POST         │ HTTP POST          │ HTTP POST
        ▼                   ▼                    ▼
┌──────────────┐   ┌────────────────┐   ┌─────────────────────┐
│  inventory-  │   │ payment-service│   │ fulfillment-service │
│   service    │   │  (Port 8082)   │   │     (Port 8083)     │
│ (Port 8081)  │   └────────────────┘   └─────────────────────┘
└──────────────┘
```

The stack consists of **4 independent Spring Boot microservices**:
1. **`order-service`** (`http://localhost:8080`): Primary API Gateway & Order Checkout Coordinator.
2. **`inventory-service`** (`http://localhost:8081`): Stock check, stock deduction, and inventory reservation lock simulations.
3. **`payment-service`** (`http://localhost:8082`): Payment processor, Stripe gateway integration, and card decline (402) simulations.
4. **`fulfillment-service`** (`http://localhost:8083`): Logistics, FedEx shipping label generation, and carrier API timeout (503) simulations.

---

## 🚀 Quick Start (One-Click Launch & Stop)

### Prerequisites
- **Docker Desktop** installed and running.
- **Node.js (v18+)** installed.
- **Java 17 JDK** installed.

### Launching the Stack
Run the automated launch script from root:

```powershell
.\run-all.ps1
```

This script automatically:
1. Stops any stale background Java processes.
2. Starts OpenObserve via Docker Compose on port `5080`.
3. Auto-downloads portable Apache Maven if not present in system PATH.
4. Launches all **4 independent Spring Boot microservices** on ports `8080`, `8081`, `8082`, and `8083`.
5. Starts the **React + Vite Frontend** on port `3000`.

### Stopping the Stack
To gracefully shut down all microservices, Node servers, and Docker containers:

```powershell
.\stop-all.ps1
```

---

## 🔗 Port & Service Directory

| Component | Port / URL | Credentials / Details |
| :--- | :--- | :--- |
| **AmzStore Web App** | `http://localhost:3000` | E-Commerce Store & Telemetry Controls |
| **OpenObserve UI** | `http://localhost:5080` | Email: `root@example.com` <br> Password: `ComplexPassword123` |
| **order-service** | `http://localhost:8080` | Main Gateway API (`/api/products`, `/api/orders/checkout`) |
| **inventory-service** | `http://localhost:8081` | Microservice Endpoint (`/api/inventory/reserve`) |
| **payment-service** | `http://localhost:8082` | Microservice Endpoint (`/api/payment/authorize`) |
| **fulfillment-service**| `http://localhost:8083` | Microservice Endpoint (`/api/fulfillment/ship`) |

---

## 📊 Telemetry Features in OpenObserve

### 1. W3C TraceContext Propagation (`RestTemplateBuilder`)
Cross-service HTTP REST calls propagate W3C `traceparent` headers using Spring Boot's auto-instrumented `RestTemplateBuilder`.
In OpenObserve:
- **Services Tab**: Displays all 4 distinct microservices (`order-service`, `inventory-service`, `payment-service`, `fulfillment-service`).
- **Traces Tab**: Renders color-coded Gantt chart waterfalls showing HTTP client/server execution durations across all 4 services.
- **Service Map**: Visual node dependency graph showing directional arrows connecting microservices.

### 2. Exception Recording & Stack Traces
Real Java custom exceptions are caught and recorded directly onto OpenTelemetry spans (`Span.current().recordException(ex)`) and logged to OpenObserve:
- `InventoryOutOfStockException` (500)
- `PaymentGatewayDeclinedException` (402)
- `CarrierServiceUnavailableException` (503)
- `DatabaseConnectionTimeoutException` (504)

Selecting any failed span in OpenObserve's **Exceptions** tab displays the full Java Exception Class, Error Message, and Stack Trace.

### 3. Real-Time Correlated Log Ingestion
- Custom Logback Appender flushes structured log events directly to OpenObserve (`/api/default/default/_json`).
- Clicking **View Logs** on any trace in OpenObserve executes an instant SQL query matching `trace_id`.

---

## 📄 License
MIT License.
