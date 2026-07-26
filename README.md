# OpenObserve E-Commerce Monitoring Demo with Spring Boot 3 & OpenTelemetry

A full-stack, multi-service e-commerce monitoring demonstration featuring an **Amazon-style React frontend**, a **Spring Boot 3 distributed microservices architecture**, and real-time observability powered by **OpenObserve** (Logs, Distributed Tracing, & Metrics).

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

## 🚀 Quick Start (One-Click Launch)

### Prerequisites
- **Docker Desktop** installed and running.
- **Node.js (v18+)** installed.
- **Java 17 JDK** installed.
- *PowerShell* (Windows).

### Running the Stack
Run the automated launch script from root:

```powershell
.\run-all.ps1
```

This script automatically:
1. Stops any stale background Java processes.
2. Starts OpenObserve via Docker Compose on port `5080`.
3. Auto-downloads portable Apache Maven if not present in your system PATH.
4. Launches all **4 independent Spring Boot microservices** on ports `8080`, `8081`, `8082`, and `8083`.
5. Starts the **React + Vite Frontend** on port `3000`.

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

### 1. Multi-Hop Distributed Tracing
OpenTelemetry W3C headers (`traceparent`) are automatically injected across all cross-service HTTP REST calls (`8080 → 8081`, `8080 → 8082`, `8080 → 8083`).
In OpenObserve:
- **Services Tab**: View all 4 distinct microservices (`order-service`, `inventory-service`, `payment-service`, `fulfillment-service`).
- **Traces Tab**: Inspect color-coded Gantt chart timelines showing parent-child HTTP client/server spans.
- **Status Codes**: Every span explicitly reports `StatusCode.OK` or `StatusCode.ERROR` with full `status_message` descriptions.

### 2. Live Log Correlation
- Custom Logback Appender flushes structured log events directly to OpenObserve (`/api/default/default/_json`).
- In OpenObserve **Logs** tab $\rightarrow$ select stream `default` $\rightarrow$ click **Run Query** to search and filter logs correlated with `trace_id`.

### 3. Failure Simulations
In the Checkout UI, select any service failure mode to test real-world error monitoring:
- **Inventory Service Failure**: Simulates DB row lock timeout / out-of-stock exception.
- **Payment Gateway Failure**: Simulates card authorization decline (HTTP 402).
- **Fulfillment Service Failure**: Simulates carrier API connection timeout (HTTP 503).

---

## 📄 License
MIT License.
