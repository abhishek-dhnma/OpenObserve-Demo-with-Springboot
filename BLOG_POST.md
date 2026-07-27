# Building Production-Grade Distributed Telemetry: Why I Replaced Heavy ELK/Jaeger Stacks with OpenObserve & Spring Boot 3

> **Author**: Abhishek Dhiman  
> **Repository**: [OpenObserve-Demo-with-Springboot](https://github.com/abhishek-dhnma/OpenObserve-Demo-with-Springboot.git)  
> **Tech Stack**: Spring Boot 3, OpenTelemetry Java SDK, Micrometer Tracing, OpenObserve (`openobserve:latest`), React + Vite  

---

## 💡 Architectural Rationale & Why OpenObserve (`latest`)

When designing observability for cloud-native microservice applications, engineering teams typically face a major trade-off: **operational overhead vs. telemetry visibility**.

Traditional observability stacks require running multiple specialized instances:
- **Elasticsearch + Logstash + Kibana (ELK)** for logs (requires 2GB+ JVM heap memory per node).
- **Jaeger / Zipkin** for distributed tracing.
- **Prometheus + Grafana** for metrics.

This fragmented stack introduces **high resource costs** and severe **context-switching fatigue** when trying to correlate a spike in HTTP 500 errors to a specific database lock or carrier API timeout.

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       TRADITIONAL FRAGMENTED STACK                          │
│                                                                             │
│  Logs: Logstash ──► Elasticsearch ──► Kibana                                │
│  Traces: Zipkin Agent ──► Jaeger Collector ──► Jaeger UI                    │
│  Metrics: Prometheus Exporter ──► Prometheus ──► Grafana                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      VS
┌─────────────────────────────────────────────────────────────────────────────┐
│                    OPENOBSERVE UNIFIED RUST ENGINE                          │
│                                                                             │
│  Logs  ──┐                                                                  │
│  Traces ─┼──► OTLP / JSON HTTP ──► OpenObserve (Port 5080) ──► Unified UI    │
│  Metrics─┘                        [Rust Engine: ~60MB RAM]                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why OpenObserve (`openobserve:latest`)?
In this project, I opted for **OpenObserve (`openobserve:latest`)**, a high-performance, unified observability platform written in Rust:
1. **Unified Storage Engine**: Logs, Traces, Metrics, and Real User Monitoring (RUM) stored under a single columnar storage engine.
2. **Resource Efficiency**: Operates on ~60MB RAM idle memory footprint compared to Elasticsearch's 2GB+ JVM heap requirement.
3. **Native OTLP Compatibility**: Direct ingestion of OpenTelemetry Protobuf traces via `/api/default/v1/traces` without needing intermediate collector sidecars.

---

## 🏗️ 4-Service Microservices Architecture & Port Topology

The demonstration platform simulates an **Amazon-style E-Commerce pipeline (`AmzStore`)** separated into 4 distinct, standalone Spring Boot microservices communicating over HTTP REST:

```text
┌────────────────────────────────────────────────────────┐
│               AmzStore Web App (Port 3000)             │
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

### Microservice Port Specification
1. **`order-service` (Port `8080`)**: Primary API Gateway & Order Checkout Coordinator (`spring.application.name=order-service`).
2. **`inventory-service` (Port `8081`)**: Standalone stock reservation service (`spring.application.name=inventory-service`).
3. **`payment-service` (Port `8082`)**: Standalone Stripe payment gateway processor (`spring.application.name=payment-service`).
4. **`fulfillment-service` (Port `8083`)**: Standalone FedEx logistics & shipping service (`spring.application.name=fulfillment-service`).

---

## 🔬 Under the Hood: W3C TraceContext Propagation (`RestTemplateBuilder`)

A common pitfall in Spring Boot microservices is instantiating `new RestTemplate()` directly. Plain `new RestTemplate()` creates an unmanaged HTTP client that **does not inject tracing headers**, causing downstream microservices to lose the parent Trace ID.

### The Solution: Managed `RestTemplateBuilder`
By registering a `RestTemplate` Spring Bean built via `RestTemplateBuilder`, Spring Boot automatically attaches `TracingClientHttpRequestInterceptor`:

```java
// File: RestTemplateConfig.java
package com.example.amzstore.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Auto-attaches OpenTelemetry W3C traceparent header interceptor!
        return builder.build();
    }
}
```

### Byte-Level Hex W3C Header Structure
When `order-service` sends an HTTP POST to `payment-service` (Port 8082), the interceptor injects the W3C `traceparent` HTTP header:

```text
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
              │  │                                │                │
            Version Trace ID (32 Hex Characters)  Parent Span ID   Sampled Flag
```

When `payment-service` receives the request, Spring's Web MVC tracing filter extracts `traceparent`, attaches its server span to the exact same Trace ID, and exports its spans under `service_name = payment-service`.

---

## 📜 High-Performance Asynchronous Log Ingestion (SLF4J + Logback + MDC)

To prevent log ingestion HTTP calls from blocking business application threads, I implemented a two-stage asynchronous logging pipeline:

```text
Application Thread  ──► SLF4J log.info() ──► Logback Appender ──► ConcurrentLinkedQueue
                                                                        │
Background Thread (@Scheduled fixedRate=1000) ◄─────────────────────────┘
        │
        └──► Batch POST (100 logs/sec) ──► OpenObserve /api/default/default/_json
```

### 1. `OpenObserveLogbackAppender.java`
Extracts `traceId` and `spanId` attached to thread-local SLF4J MDC (Mapped Diagnostic Context) by Micrometer Tracing:

```java
package com.example.amzstore.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class OpenObserveLogbackAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null) return;

        String traceId = "none";
        String spanId = "none";

        if (eventObject.getMDCPropertyMap() != null) {
            traceId = eventObject.getMDCPropertyMap().getOrDefault("traceId", "none");
            spanId = eventObject.getMDCPropertyMap().getOrDefault("spanId", "none");
        }

        OpenObserveLogPublisher.queueLog(
                eventObject.getLevel().toString(),
                eventObject.getLoggerName(),
                eventObject.getFormattedMessage(),
                traceId,
                spanId
        );
    }
}
```

---

## 💥 Custom Exception Recording & Root Cause Analysis

To make telemetry truly actionable, failure simulations in each microservice instantiate real custom Java exceptions, record them on OpenTelemetry spans, and log full stack traces:

### Exception Classes
- `InventoryOutOfStockException` (HTTP 500)
- `PaymentGatewayDeclinedException` (HTTP 402)
- `CarrierServiceUnavailableException` (HTTP 503)
- `DatabaseConnectionTimeoutException` (HTTP 504)

```java
// Example from PaymentController.java (Port 8082)
if (simulateFailure) {
    RuntimeException ex = new PaymentGatewayDeclinedException(
        "Card authorization declined by issuing bank (Code 402)"
    );
    log.error("[payment-service] Payment authorization REJECTED for OrderID: {}", orderId, ex);
    
    // Explicitly record exception on OpenTelemetry span
    Span.current().recordException(ex);
    Span.current().setStatus(StatusCode.ERROR, ex.getMessage());
    
    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(Map.of("error", ex.getMessage(), "code", "PAYMENT_DECLINED_402"));
}
```

When inspecting a failed trace in OpenObserve, the **Exceptions** tab displays:
- **Exception Class**: `com.example.amzstore.exception.PaymentGatewayDeclinedException`
- **Error Message**: `Card authorization declined by issuing bank (Code 402)`
- **Stack Trace**: Full Java call stack for pinpoint root cause diagnosis.

---

## 📊 Live OpenObserve Visualizations

### 1. Distributed Trace Waterfall & Multi-Service Gantt Chart
![OpenObserve Trace Waterfall](file:///C:/Users/abhishek%20dhiman/.gemini/antigravity-ide/brain/5211dbf6-697f-4623-8373-9243184f43b5/media__1785061425920.png)

- Displays distinct color-coded spans for `order-service` (brown), `inventory-service` (cyan), `payment-service` (orange), and `fulfillment-service` (salmon).
- Clearly highlights exact latency contributions across network hops.

### 2. Error Span & Exception Inspection
![OpenObserve Error Span Analysis](file:///C:/Users/abhishek%20dhiman/.gemini/antigravity-ide/brain/5211dbf6-697f-4623-8373-9243184f43b5/media__1785061550890.png)

- OpenObserve flags failing spans in red and exposes span attributes (`status_code = ERROR`, `status_message = PaymentGateway Authorization Failed...`).

---

## ⚡ Business Metrics & Financial Telemetry

In addition to traces and logs, custom Micrometer counters and timers (`MetricsConfig.java`) track business performance metrics:
- **`amzstore_orders_success_total`**: Counter of successful orders.
- **`amzstore_orders_failure_total`**: Counter of failed orders tagged by error reason.
- **`amzstore_checkout_latency_seconds`**: End-to-end checkout pipeline latency percentiles (`p50`, `p95`, `p99`).

---

## 🛠️ One-Click Deployment Scripts

To make running and testing painless:
- **Start Stack**: `.\run-all.ps1` (Launches OpenObserve `latest`, 4 Spring Boot microservices, and React frontend).
- **Stop Stack**: `.\stop-all.ps1` (Gracefully terminates Java processes, Node servers, and Docker containers).

---

## 🎯 Summary of Key Engineering Takeaways

1. **`openobserve:latest`** delivers lightweight, unified observability with a fraction of the RAM overhead of legacy JVM monitoring tools.
2. **`RestTemplateBuilder`** is required in Spring Boot 3 for auto-injecting W3C `traceparent` headers across HTTP REST microservice boundaries.
3. **Recording Java Exceptions on OpenTelemetry Spans** (`Span.current().recordException(ex)`) eliminates diagnostic guesswork during production incidents.
