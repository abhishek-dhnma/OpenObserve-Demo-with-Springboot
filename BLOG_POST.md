# Monitoring SpringBoot Applications: Logs and Traces with OpenObserve

> **Author:** OpenObserve Technical Community  
> **Topic:** Full-Stack Observability with Spring Boot 3, OpenTelemetry, and OpenObserve  
> **Repository Sample App:** `AmzStore E-Commerce Platform`  

---

## Executive Summary

In modern cloud-native architectures, microservices and distributed Spring Boot applications process thousands of requests per second. When an outage or unexpected payment failure occurs, traditional file-based logging is no longer sufficient. Developers need **end-to-end observability**—the ability to correlate high-level user actions with low-level application logs and multi-service distributed traces.

In this comprehensive guide, we will demonstrate how to set up **OpenObserve** locally using **Docker Desktop** and instrument a **Spring Boot 3** application (**AmzStore**) with **OpenTelemetry (OTLP)** and **Micrometer Tracing** to achieve real-time log analysis, trace visualization, and seamless log-trace correlation.

---

## Table of Contents

1. [Understanding Observability: Logs vs. Traces](#1-understanding-observability-logs-vs-traces)
2. [Why OpenObserve?](#2-why-openobserve)
3. [System Architecture](#3-system-architecture)
4. [Prerequisites & OpenObserve Docker Setup](#4-prerequisites--openobserve-docker-setup)
5. [Configuring Spring Boot 3 with OpenTelemetry & OTLP](#5-configuring-spring-boot-3-with-opentelemetry--otlp)
6. [Application Implementation: AmzStore E-Commerce](#6-application-implementation-amzstore-e-commerce)
7. [Analyzing Logs and Traces in OpenObserve](#7-analyzing-logs-and-traces-in-openobserve)
8. [Conclusion & Best Practices](#8-conclusion--best-practices)

---

## 1. Understanding Observability: Logs vs. Traces

Observability is built on three core pillars: **Logs, Metrics, and Traces**.

* **Logs**: Discrete timestamped event records (e.g., `Order #102 created`, `Payment failed: Card declined`). Logs provide rich textual details about what happened at a specific instant.
* **Traces**: Represent the end-to-end request journey across multiple functions, database queries, and microservice boundaries. A trace consists of individual **spans**, where each span measures the latency of a single operation.
* **The Log-Trace Correlation Challenge**: In traditional setups, searching for an error log requires knowing the exact timestamp. With **Log-Trace Correlation**, every log statement automatically embeds a `trace_id`. In OpenObserve, clicking a trace span instantly shows all corresponding application logs, and clicking an error log reveals the exact execution waterfall!

---

## 2. Why OpenObserve?

**OpenObserve** is a high-performance, open-source observability platform designed as a lightweight alternative to Elasticsearch, Grafana Loki, and Datadog.

### Key Advantages:
* **Stateless & High Compression**: Uses Rust and Apache Arrow/Parquet for up to **140x lower storage costs**.
* **Unified Telemetry**: Native support for **Logs, Traces, Metrics, and RUM (Real User Monitoring)** in a single dashboard.
* **SQL Query Engine**: Supports standard SQL queries (`SELECT * FROM default WHERE level='ERROR'`) for intuitive data exploration.
* **Zero-Lock-In OpenTelemetry (OTLP) Support**: Consumes standard OpenTelemetry protocol streams over HTTP/gRPC without requiring proprietary agents.

---

## 3. System Architecture

Below is the architectural flow of our complete setup:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            AmzStore Frontend (React)                        │
│ - Browses Products, Adds to Cart, Triggers Checkout & Error Simulations     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTP Requests (JSON)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Spring Boot 3 Backend (Port 8080)                    │
│                                                                             │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌───────────────────┐  │
│  │ Product & Order REST │  │ Micrometer Tracing   │  │ Logback Logger    │  │
│  │ Controllers          │  │ (Generates Trace IDs)│  │ (MDC Injection)   │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └─────────┬─────────┘  │
└─────────────┼─────────────────────────┼────────────────────────┼────────────┘
              │                         │ OTLP Traces            │ HTTP Logs
              │                         ▼                        ▼
┌─────────────┴───────────────────────────────────────────────────────────────┐
│                    OpenObserve Container (Port 5080)                        │
│                                                                             │
│ - Streams Engine: Stores Logs & Traces under 'default' stream               │
│ - Dashboard UI: Real-time Trace Waterfalls & Log Filtering                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Prerequisites & OpenObserve Docker Setup

### Prerequisites
* Java 17+ & Maven 3.8+
* Docker Desktop (Running on Windows/Mac/Linux)
* Node.js 18+ (for running the demo frontend)

### Docker Desktop Setup

Running OpenObserve locally with Docker Desktop avoids requiring corporate domain verification.

#### `docker-compose.yml`
```yaml
version: '3.8'

services:
  openobserve:
    image: openobserve/openobserve:v0.14.4
    container_name: openobserve
    restart: always
    environment:
      - ZO_ROOT_USER_EMAIL=root@example.com
      - ZO_ROOT_USER_PASSWORD=ComplexPassword123
      - ZO_HTTP_PORT=5080
      - ZO_DATA_DIR=/data
    ports:
      - "5080:5080"
    volumes:
      - openobserve-data:/data

volumes:
  openobserve-data:
```

#### Launch Command:
```bash
docker-compose up -d
```

Once running, navigate to `http://localhost:5080` in your web browser and sign in with:
* **Username**: `root@example.com`
* **Password**: `ComplexPassword123`

---

## 5. Configuring Spring Boot 3 with OpenTelemetry & OTLP

### A. Maven Dependencies (`pom.xml`)

In Spring Boot 3, **Micrometer Tracing** acts as the abstraction layer, while `micrometer-tracing-bridge-otel` provides the OpenTelemetry bridge. The `opentelemetry-exporter-otlp` package transmits trace spans directly to OpenObserve via HTTP/OTLP.

```xml
<dependencies>
    <!-- Spring Boot Starter Web & Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Micrometer Tracing + OpenTelemetry Bridge -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <!-- OpenTelemetry OTLP Exporter -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>
</dependencies>
```

---

### B. Application Configuration (`application.yml`)

Configure Micrometer and OpenTelemetry properties to point to the OpenObserve OTLP endpoint:

```yaml
server:
  port: 8080

spring:
  application:
    name: amzstore-backend

management:
  tracing:
    sampling:
      probability: 1.0 # Sample 100% of requests for demo purposes
  otlp:
    tracing:
      endpoint: http://localhost:5080/api/default/v1/traces
      headers:
        Authorization: "Basic cm9vdEBleGFtcGxlLmNvbTpDb21wbGV4UGFzc3dvcmQxMjM="

openobserve:
  url: http://localhost:5080
  auth-header: "Basic cm9vdEBleGFtcGxlLmNvbTpDb21wbGV4UGFzc3dvcmQxMjM="
```

---

### C. Logging Configuration (`logback-spring.xml`)

Ensure every console log statement formats `traceId` and `spanId` from MDC:

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId:-none} spanId=%X{spanId:-none}] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

---

## 6. Application Implementation: AmzStore E-Commerce

Our sample application implements a multi-span order processing flow:

### `OrderService.java` (Multi-Span Distributed Tracing)

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final Tracer tracer;

    public Order processCheckout(CheckoutRequest request) {
        String mainTraceId = tracer.currentSpan() != null ? 
            tracer.currentSpan().context().traceId() : UUID.randomUUID().toString();
        String orderId = "ORD-" + System.currentTimeMillis();

        log.info("Starting order checkout process for Customer: {} [OrderID: {}]", 
            request.getCustomerEmail(), orderId);

        // Step 1: Child Span for Stock Validation
        validateStockAndCart(request.getItems(), orderId);

        // Step 2: Child Span for Payment Gateway Authorization
        boolean paymentSuccess = processPaymentGateway(orderId, request.getTotalAmount(), request.isSimulateFailure());

        if (!paymentSuccess) {
            log.error("Order processing failed at Payment Gateway step for OrderID: {}", orderId);
            return Order.builder().status("FAILED").traceId(mainTraceId).build();
        }

        log.info("Order successfully completed! OrderID: {}, TraceID: {}", orderId, mainTraceId);
        return Order.builder().status("PAID").traceId(mainTraceId).build();
    }

    private void validateStockAndCart(List<CartItem> items, String orderId) {
        ScopedSpan span = tracer.startScopedSpan("validateStockAndCart");
        try {
            span.tag("order.id", orderId);
            log.info("Validating inventory stock for OrderID: {}", orderId);
            Thread.sleep(120); // Database check latency simulation
        } catch (InterruptedException ignored) {
        } finally {
            span.end();
        }
    }

    private boolean processPaymentGateway(String orderId, BigDecimal amount, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("processPaymentGateway");
        try {
            span.tag("order.id", orderId);
            if (simulateFailure) {
                span.error(new RuntimeException("Payment Gateway Error: Card declined by issuing bank"));
                log.error("Payment Gateway returned error for OrderID: {}. Card declined!", orderId);
                return false;
            }
            return true;
        } finally {
            span.end();
        }
    }
}
```

---

## 7. Analyzing Logs and Traces in OpenObserve

### Step 1: Viewing Live Logs Stream
1. Open `http://localhost:5080` $\rightarrow$ Navigate to **Logs** tab.
2. Select stream: `default`.
3. Run SQL query:
   ```sql
   SELECT _timestamp, level, message, trace_id, span_id 
   FROM "default" 
   WHERE level = 'ERROR' 
   ORDER BY _timestamp DESC
   ```
4. Observe that every log line contains `trace_id` and `span_id`.

---

### Step 2: Distributed Trace Visualization
1. Navigate to the **Traces** tab in OpenObserve.
2. Paste a `trace_id` generated during an order checkout (e.g. from the UI Toast notification).
3. OpenObserve displays a full **Gantt Chart Waterfall Diagram**:
   - `HTTP POST /api/orders/checkout` (Root Span)
     - `validateStockAndCart` (Child Span - 120ms)
     - `processPaymentGateway` (Child Span - 250ms)

---

### Step 3: Log-Trace Correlation in Action
* Click on any trace span in the OpenObserve UI.
* The side drawer automatically loads all logs associated with that specific `trace_id`.
* This instant correlation reduces Mean Time to Resolution (**MTTR**) from hours to seconds!

---

## 8. Conclusion & Best Practices

Setting up full-stack observability with **Spring Boot 3** and **OpenObserve** via **Docker Desktop** provides a frictionless developer experience. By combining OpenTelemetry standards with OpenObserve's lightweight storage engine, engineering teams gain enterprise-grade monitoring without massive cloud costs.

### Recommended Next Steps:
1. Enable metrics collection using `micrometer-registry-prometheus` or OTLP metrics exporter.
2. Configure threshold alerts in OpenObserve for `ERROR` log bursts.
3. Explore OpenObserve SQL Dashboards for real-time latency heatmaps.
