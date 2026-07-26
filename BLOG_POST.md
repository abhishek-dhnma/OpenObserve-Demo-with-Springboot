# Mastering Distributed Microservices Telemetry: Monitoring Spring Boot 3 Applications with OpenObserve & OpenTelemetry

> **Author**: Abhishek Dhiman  
> **Topic**: Cloud-Native Observability, Distributed Tracing, Structured Logging  
> **Stack**: Spring Boot 3, OpenTelemetry, Logback, OpenObserve, React + Vite  

---

## 📌 Executive Summary & Introduction

In modern cloud-native architectures, applications are built as decoupled microservices communicating across network boundaries over HTTP, gRPC, and message brokers. While microservice architectures provide exceptional scalability and feature independence, they introduce significant **operational complexity**:

1. **Monolithic Logs vs. Distributed Traces**: In a monolith, searching a single server log file (`app.log`) is usually sufficient to debug a failure. In a microservices architecture, a single user click (e.g., placing an order) triggers multiple downstream HTTP requests across an API Gateway, Auth Service, Inventory Service, Payment Service, and Fulfillment Service.
2. **The "Needle in a Haystack" Problem**: Without unified tracing, pinpointing which specific service introduced latency or caused a `500 Internal Error` requires manually scanning disconnected log streams across dozens of server nodes.
3. **Why OpenObserve?**: OpenObserve is an open-source, high-performance observability platform that serves as a lightweight, cost-effective alternative to Elasticsearch, Datadog, and Splunk. It unifies **Logs**, **Traces**, and **Metrics** in a single user interface with zero-setup SQL querying.

In this guide, we walk step-by-step through configuring **Spring Boot 3** with **OpenTelemetry** and **Logback** to stream live distributed traces and correlated logs into a local **OpenObserve** monitoring instance.

---

## 🛠️ Step 1: Setting Up OpenObserve via Docker

To run OpenObserve locally without cloud email dependencies, we deploy OpenObserve using Docker Desktop.

### `docker-compose.yml`
```yaml
version: '3.8'

services:
  openobserve:
    image: openobserve/openobserve:v0.14.3
    container_name: openobserve
    restart: always
    environment:
      - ZO_ROOT_USER_EMAIL=root@example.com
      - ZO_ROOT_USER_PASSWORD=ComplexPassword123
      - ZO_DATA_DIR=/data
      - ZO_HTTP_PORT=5080
    ports:
      - "5080:5080"
    volumes:
      - openobserve-data:/data

volumes:
  openobserve-data:
```

### Launch Command
```powershell
docker-compose up -d
```
OpenObserve initializes on `http://localhost:5080` with initial credentials:
- **Email**: `root@example.com`
- **Password**: `ComplexPassword123`

---

## ⚙️ Step 2: Spring Boot 3 & OpenTelemetry Dependencies

To instrument a Spring Boot 3 application for tracing, add Micrometer Tracing with the OpenTelemetry bridge and the OTLP exporter to `pom.xml`.

### `pom.xml`
```xml
<dependencies>
    <!-- Spring Boot Web & Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Micrometer Tracing Bridge to OpenTelemetry -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <!-- OpenTelemetry OTLP Exporter -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 🌐 Step 3: Application Configuration (`application.yml`)

Configure OpenTelemetry trace sampling and point the OTLP exporter to OpenObserve's OTLP trace ingestion endpoint (`/api/default/v1/traces`).

```yaml
server:
  port: 8080

spring:
  application:
    name: order-service

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env
  tracing:
    sampling:
      probability: 1.0 # 100% trace sampling for demo
  otlp:
    tracing:
      endpoint: http://localhost:5080/api/default/v1/traces
      headers:
        Authorization: "Basic cm9vdEBleGFtcGxlLmNvbTpDb21wbGV4UGFzc3dvcmQxMjM="

openobserve:
  url: http://localhost:5080
  auth-header: "Basic cm9vdEBleGFtcGxlLmNvbTpDb21wbGV4UGFzc3dvcmQxMjM="
  organization: default
  stream: default
```

---

## 📜 Step 4: Real-time Log Ingestion with MDC & Logback

To stream application logs directly to OpenObserve and correlate each log entry with its active `traceId`, we create a custom Logback Appender and a background batch publisher.

### 1. `OpenObserveLogbackAppender.java`
```java
package com.example.amzstore.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class OpenObserveLogbackAppender extends AppenderBase<ILoggingEvent> {

    @Override
    public void start() {
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null) return;

        // Extract traceId and spanId attached to thread MDC by Micrometer Tracing
        String traceId = "none";
        String spanId = "none";

        if (eventObject.getMDCPropertyMap() != null) {
            traceId = eventObject.getMDCPropertyMap().getOrDefault("traceId", "none");
            spanId = eventObject.getMDCPropertyMap().getOrDefault("spanId", "none");
        }

        // Queue log entry for background flush
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

### 2. `OpenObserveLogPublisher.java`
```java
package com.example.amzstore.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OpenObserveLogPublisher {

    @Value("${openobserve.url:http://localhost:5080}")
    private String openobserveUrl;

    @Value("${openobserve.auth-header:Basic cm9vdEBleGFtcGxlLmNvbTpDb21wbGV4UGFzc3dvcmQxMjM=}")
    private String authHeader;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final ConcurrentLinkedQueue<Map<String, Object>> logQueue = new ConcurrentLinkedQueue<>();

    public static void queueLog(String level, String loggerName, String message, String traceId, String spanId) {
        if (loggerName != null && loggerName.contains("OpenObserveLogPublisher")) return;

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("_timestamp", Instant.now().toEpochMilli() * 1000); // Microseconds for OpenObserve
        logEntry.put("level", level);
        logEntry.put("logger", loggerName != null ? loggerName : "ApplicationLogger");
        logEntry.put("message", message);
        logEntry.put("service_name", "order-service");
        logEntry.put("trace_id", traceId != null ? traceId : "none");
        logEntry.put("span_id", spanId != null ? spanId : "none");

        logQueue.add(logEntry);
    }

    @Scheduled(fixedRate = 1000)
    public void flushLogsToOpenObserve() {
        if (logQueue.isEmpty()) return;

        List<Map<String, Object>> batch = new ArrayList<>();
        while (!logQueue.isEmpty() && batch.size() < 100) {
            Map<String, Object> entry = logQueue.poll();
            if (entry != null) batch.add(entry);
        }

        if (batch.isEmpty()) return;

        try {
            String endpoint = openobserveUrl + "/api/default/default/_json";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader);

            HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(batch, headers);
            restTemplate.postForEntity(endpoint, entity, String.class);
        } catch (Exception ignored) {}
    }
}
```

### 3. `logback-spring.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId:-none} spanId=%X{spanId:-none}] - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="OPENOBSERVE" class="com.example.amzstore.config.OpenObserveLogbackAppender"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="OPENOBSERVE"/>
    </root>

</configuration>
```

---

## 🔗 Step 5: Implementing Multi-Service Microservices Tracing

In our e-commerce platform (`AmzStore`), when a customer places an order, the `order-service` gateway executes cross-service HTTP REST calls to `inventory-service` (Port 8081), `payment-service` (Port 8082), and `fulfillment-service` (Port 8083).

### `OrderService.java` Implementation
```java
package com.example.amzstore.service;

import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final Tracer tracer;
    private final RestTemplate restTemplate = new RestTemplate();

    public Order processCheckout(CheckoutRequest request) {
        String orderId = "ORD-" + System.currentTimeMillis();
        
        // 1. Local AuthService Hop
        validateCustomerSession(request.getCustomerEmail(), orderId);

        // 2. HTTP Hop to Inventory Microservice (Port 8081)
        reserveInventoryStock(orderId, request.isSimulateFailure());

        // 3. HTTP Hop to Payment Gateway Microservice (Port 8082)
        authorizePayment(orderId, request.isSimulateFailure());

        // 4. HTTP Hop to Logistics Fulfillment Microservice (Port 8083)
        createShippingLabel(orderId, request.isSimulateFailure());

        return new Order(orderId, "PAID");
    }

    private boolean authorizePayment(String orderId, boolean simulateFailure) {
        ScopedSpan span = tracer.startScopedSpan("Call :: payment-service (Port 8082)");
        try {
            span.tag("microservice", "payment-service");
            span.tag("order.id", orderId);

            String url = "http://localhost:8082/api/payment/authorize?simulateFailure=" + simulateFailure + "&orderId=" + orderId;
            restTemplate.postForEntity(url, null, Map.class);

            Span.current().setStatus(StatusCode.OK, "HTTP POST to payment-service (Port 8082) succeeded");
            return true;
        } catch (Exception e) {
            String errorMsg = "HTTP POST to payment-service failed: Card authorization declined (Code 402)";
            span.error(e);
            Span.current().setStatus(StatusCode.ERROR, errorMsg);
            span.tag("error.code", "PAYMENT_DECLINED_402");
            return false;
        } finally {
            span.end();
        }
    }
}
```

---

## 📈 Step 6: Analyzing Logs & Traces in OpenObserve

### 1. Distributed Trace Waterfall & Gantt Timeline Analysis

When an order checkout request executes, OpenObserve renders a full visual **Gantt Chart Timeline** showing every child span and microservice execution hop:

![OpenObserve Trace Gantt Chart Timeline](file:///C:/Users/abhishek%20dhiman/.gemini/antigravity-ide/brain/5211dbf6-697f-4623-8373-9243184f43b5/media__1785061425920.png)

#### Why This View Matters:
- **Latency Breakdown**: Shows the exact millisecond duration of each hop (`AuthService`: 45ms, `InventoryService`: 110ms, `PaymentGatewayService`: 210ms, `FulfillmentService`: 140ms).
- **Hierarchy & Span Relationships**: Clearly demarcates parent HTTP calls from child service execution blocks.
- **Span Status**: Selecting a span displays `span_status: OK` (or `ERROR`), `status_code: 1`, and `status_message: FraudDetectionService: Transaction risk score 0.02 (Low / Approved)`.

---

### 2. Failure Diagnosis & Error Span Inspection

When simulating a service outage (e.g. **Payment Gateway Failure**), OpenObserve flags the failed span in red and captures the underlying error context:

![OpenObserve Error Span Analysis](file:///C:/Users/abhishek%20dhiman/.gemini/antigravity-ide/brain/5211dbf6-697f-4623-8373-9243184f43b5/media__1785061550890.png)

#### Why This View Matters:
- **Instant Root-Cause Identification**: Highlights `PaymentGatewayService :: authorizePayment` with `error.code: PAYMENT_DECLINED_402`.
- **Span Status Metadata**: Shows `status_code: ERROR` and `status_message: PaymentGateway Authorization Failed: Card declined by issuing bank (Code 402)`.
- **Zero Guesswork**: Engineers can immediately identify whether a failure was caused by a database lock, network timeout, or third-party API decline without digging through server logs manually.

---

### 3. Log Ingestion & SQL Querying in Stream `default`

Navigating to the **Logs** tab in OpenObserve displays real-time structured logs streamed from Logback:

![OpenObserve Logs Stream Search](file:///C:/Users/abhishek%20dhiman/.gemini/antigravity-ide/brain/5211dbf6-697f-4623-8373-9243184f43b5/media__1785059718843.png)

#### Why This View Matters:
- **Correlated `trace_id` Search**: Clicking **View Logs** from any trace executes an instant SQL query (`SELECT * FROM "default" WHERE trace_id = '802c4a6817906849...'`).
- **Structured Fields**: Filters logs by `level`, `logger`, `service_name`, and timestamp.

---

## 🎯 Conclusion & Best Practices

Integrating **Spring Boot 3**, **OpenTelemetry**, and **OpenObserve** creates a robust, production-ready observability foundation:

1. **Always propagate W3C Trace Context**: Use `RestTemplate` or `WebClient` so HTTP client calls automatically forward `traceparent` headers.
2. **Set Explicit Span Statuses**: Always call `Span.current().setStatus(StatusCode.ERROR, errorMessage)` on catch blocks so OpenObserve highlights failing spans in red.
3. **Leverage SLF4J MDC**: Include `traceId` and `spanId` in your logging appenders for instant log-to-trace correlation.

With OpenObserve running locally in Docker, you get Enterprise-grade distributed tracing and log search with zero SaaS subscription fees!
