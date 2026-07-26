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
