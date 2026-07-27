# OpenObserve & Spring Boot 3 Microservices Architecture Specification

This document provides a comprehensive technical overview of the **AmzStore Multi-Service Telemetry System**. It details the distributed microservices architecture, network communication paths, OpenTelemetry W3C tracecontext propagation, and real-time log ingestion pipeline into OpenObserve.

---

## 📐 System Architecture Diagram

```mermaid
graph TD
    User([👤 User / Browser]) -->|HTTP Port 3000| Frontend[🛒 AmzStore React + Vite App]
    Frontend -->|POST /api/orders/checkout| Gateway[⚙️ order-service Gateway\nPort 8080]

    subgraph "Spring Boot 3 Microservices Cluster"
        Gateway -->|HTTP POST W3C traceparent| Inv[📦 inventory-service\nPort 8081]
        Gateway -->|HTTP POST W3C traceparent| Pay[💳 payment-service\nPort 8082]
        Gateway -->|HTTP POST W3C traceparent| Ful[🚚 fulfillment-service\nPort 8083]
    end

    subgraph "Observability & Telemetry Infrastructure"
        Gateway -->|OTLP Traces / Logback JSON| OO[🔭 OpenObserve Dashboard\nPort 5080]
        Inv -->|OTLP Traces / Logback JSON| OO
        Pay -->|OTLP Traces / Logback JSON| OO
        Ful -->|OTLP Traces / Logback JSON| OO
    end

    classDef frontend fill:#0284c7,stroke:#0369a1,color:#fff,font-weight:bold;
    classDef gateway fill:#0f766e,stroke:#115e59,color:#fff,font-weight:bold;
    classDef service fill:#4338ca,stroke:#3730a3,color:#fff,font-weight:bold;
    classDef telemetry fill:#b45309,stroke:#78350f,color:#fff,font-weight:bold;

    class Frontend frontend;
    class Gateway gateway;
    class Inv,Pay,Ful service;
    class OO telemetry;
```

---

## ⏱️ Distributed Tracing Sequence Diagram (W3C Context Propagation)

The sequence diagram below demonstrates how a single checkout click propagates a unified **Trace ID** across all 4 independent microservices using OpenTelemetry `traceparent` headers:

```mermaid
sequenceDiagram
    autonumber
    actor Customer as 👤 Customer
    participant React as 🛒 React Frontend (Port 3000)
    participant OrderService as ⚙️ order-service (Port 8080)
    participant Inventory as 📦 inventory-service (Port 8081)
    participant Payment as 💳 payment-service (Port 8082)
    participant Fulfillment as 🚚 fulfillment-service (Port 8083)
    participant OpenObserve as 🔭 OpenObserve (Port 5080)

    Customer->>React: Click "Place Your Order"
    React->>OrderService: POST /api/orders/checkout
    Note over OrderService: Generate Trace ID (e.g. 4bf92f3577...)
    
    OrderService->>Inventory: POST /api/inventory/reserve\n(Header: traceparent=00-4bf92f3577...-span1-01)
    Note over Inventory: Execute Stock Lock
    Inventory-->>OrderService: 200 OK (Reserved)

    OrderService->>Payment: POST /api/payment/authorize\n(Header: traceparent=00-4bf92f3577...-span2-01)
    Note over Payment: Authorize Stripe Gateway
    Payment-->>OrderService: 200 OK (Authorized)

    OrderService->>Fulfillment: POST /api/fulfillment/ship\n(Header: traceparent=00-4bf92f3577...-span3-01)
    Note over Fulfillment: Generate FedEx Shipping Label
    Fulfillment-->>OrderService: 200 OK (Shipped)

    par Async Telemetry Streaming
        OrderService-->>OpenObserve: OTLP Spans & Logback JSON
        Inventory-->>OpenObserve: OTLP Spans & Logback JSON
        Payment-->>OpenObserve: OTLP Spans & Logback JSON
        Fulfillment-->>OpenObserve: OTLP Spans & Logback JSON
    end

    OrderService-->>React: 200 OK (Order Receipt + Trace ID)
    React-->>Customer: Render Order Confirmation & Trace ID
```

---

## 📡 Microservice Specification & Port Matrix

| Service Name | Port | Base Directory | Description | Trace Resource Attribute (`service.name`) |
| :--- | :--- | :--- | :--- | :--- |
| **AmzStore Frontend** | `3000` | `frontend/` | React + Vite UI with Amazon checkout flow & failure simulators | N/A (Browser Client) |
| **`order-service`** | `8080` | `backend/` | Order API Gateway, Cart Manager, and Pipeline Coordinator | `order-service` |
| **`inventory-service`**| `8081` | `services/inventory-service` | Stock reservation and DB row lock timeout failure simulator | `inventory-service` |
| **`payment-service`** | `8082` | `services/payment-service` | Stripe payment processor and card decline (402) simulator | `payment-service` |
| **`fulfillment-service`**| `8083` | `services/fulfillment-service` | FedEx shipping label generator and carrier API timeout (503) simulator | `fulfillment-service` |
| **OpenObserve UI** | `5080` | Docker Container | Centralized Observability Engine (Logs, Traces, Metrics) | `default` Stream / `default` Org |

---

## ⚙️ Log & Trace Ingestion Pipeline Architecture

```mermaid
flowcard
subgraph Microservice Application
    SLF4J[SLF4J Logger] --> Logback[Logback Framework]
    Micrometer[Micrometer Tracing] --> OTel[OpenTelemetry SDK]
end

subgraph Log Ingestion Engine
    Logback --> Appender[OpenObserveLogbackAppender]
    Appender --> Queue[ConcurrentLinkedQueue]
    Queue -->|Scheduled 1s Batch| HTTPLog[POST http://localhost:5080/api/default/default/_json]
end

subgraph Trace Ingestion Engine
    OTel --> OTLPExporter[OTLP Protobuf/HTTP Exporter]
    OTLPExporter --> HTTPTrace[POST http://localhost:5080/api/default/v1/traces]
end

HTTPLog --> OpenObserve[OpenObserve Storage Engine]
HTTPTrace --> OpenObserve
```

---

## 📄 File Deliverables
- **PNG Diagram**: [`architecture_diagram.png`](file:///c:/Users/abhishek%20dhiman/Desktop/OpenObserve/architecture_diagram.png)
- **Markdown Specification**: [`ARCHITECTURE.md`](file:///c:/Users/abhishek%20dhiman/Desktop/OpenObserve/ARCHITECTURE.md)
