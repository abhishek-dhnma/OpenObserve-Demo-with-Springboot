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
        if (loggerName != null && loggerName.contains("OpenObserveLogPublisher")) {
            return;
        }

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("_timestamp", Instant.now().toEpochMilli() * 1000); // Microseconds for OpenObserve
        logEntry.put("level", level);
        logEntry.put("logger", loggerName != null ? loggerName : "ApplicationLogger");
        logEntry.put("message", message);
        logEntry.put("service_name", "amzstore-backend");
        logEntry.put("trace_id", traceId != null ? traceId : "none");
        logEntry.put("span_id", spanId != null ? spanId : "none");

        logQueue.add(logEntry);
    }

    @Scheduled(fixedRate = 1000)
    public void flushLogsToOpenObserve() {
        if (logQueue.isEmpty()) {
            return;
        }

        List<Map<String, Object>> batch = new ArrayList<>();
        while (!logQueue.isEmpty() && batch.size() < 100) {
            Map<String, Object> entry = logQueue.poll();
            if (entry != null) {
                batch.add(entry);
            }
        }

        if (batch.isEmpty()) return;

        try {
            // OpenObserve high-performance JSON ingestion endpoint for stream 'default'
            String endpoint = openobserveUrl + "/api/default/default/_json";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader);

            HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(batch, headers);
            restTemplate.postForEntity(endpoint, entity, String.class);
        } catch (Exception e) {
            // Silently swallow retries if OpenObserve is initializing
        }
    }
}
