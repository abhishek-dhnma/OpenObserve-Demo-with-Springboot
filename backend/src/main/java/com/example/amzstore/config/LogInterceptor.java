package com.example.amzstore.config;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogInterceptor implements HandlerInterceptor {

    private final Tracer tracer;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "none";
        String spanId = tracer.currentSpan() != null ? tracer.currentSpan().context().spanId() : "none";

        String msg = String.format("HTTP %s %s from %s", request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        OpenObserveLogPublisher.queueLog("INFO", "HTTP_REQUEST", msg, traceId, spanId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String traceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "none";
        String spanId = tracer.currentSpan() != null ? tracer.currentSpan().context().spanId() : "none";

        String level = response.getStatus() >= 400 ? "ERROR" : "INFO";
        String msg = String.format("HTTP %s %s completed with status %d", request.getMethod(), request.getRequestURI(), response.getStatus());

        if (ex != null) {
            msg += " Exception: " + ex.getMessage();
        }

        OpenObserveLogPublisher.queueLog(level, "HTTP_RESPONSE", msg, traceId, spanId);
    }
}
