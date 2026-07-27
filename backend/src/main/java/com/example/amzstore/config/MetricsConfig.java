package com.example.amzstore.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter successfulOrdersCounter(MeterRegistry registry) {
        return Counter.builder("amzstore_orders_success_total")
                .description("Total number of successfully placed e-commerce orders")
                .tag("service", "order-service")
                .register(registry);
    }

    @Bean
    public Counter failedOrdersCounter(MeterRegistry registry) {
        return Counter.builder("amzstore_orders_failure_total")
                .description("Total number of failed e-commerce checkout attempts")
                .tag("service", "order-service")
                .register(registry);
    }

    @Bean
    public Timer checkoutTimer(MeterRegistry registry) {
        return Timer.builder("amzstore_checkout_latency_seconds")
                .description("End-to-end latency histogram for checkout order pipeline")
                .tag("service", "order-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
