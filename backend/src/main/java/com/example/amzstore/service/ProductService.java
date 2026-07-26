package com.example.amzstore.service;

import com.example.amzstore.model.Product;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final Tracer tracer;
    private final Map<String, Product> productCatalog = new ConcurrentHashMap<>();

    @PostConstruct
    public void initCatalog() {
        List<Product> initialProducts = List.of(
            Product.builder()
                .id("PROD-001")
                .name("Echo Dot (5th Gen) Smart Speaker")
                .category("Electronics")
                .price(new BigDecimal("49.99"))
                .rating(4.7)
                .stock(45)
                .imageUrl("https://images.unsplash.com/photo-1543512214-318c7553f230?w=500")
                .description("Vibrant sound smart speaker with Alexa built-in.")
                .build(),
            Product.builder()
                .id("PROD-002")
                .name("Kindle Paperwhite (16 GB)")
                .category("Electronics")
                .price(new BigDecimal("139.99"))
                .rating(4.8)
                .stock(18)
                .imageUrl("https://images.unsplash.com/photo-1592478411213-6153e4ebc07d?w=500")
                .description("6.8 display with thinner borders and warm light feature.")
                .build(),
            Product.builder()
                .id("PROD-003")
                .name("Fire TV Stick 4K Max")
                .category("Electronics")
                .price(new BigDecimal("59.99"))
                .rating(4.6)
                .stock(3) // Low stock to trigger WARN log scenario
                .imageUrl("https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=500")
                .description("Cinematic 4K streaming with Wi-Fi 6 support.")
                .build(),
            Product.builder()
                .id("PROD-004")
                .name("Sony WH-1000XM5 Wireless Headphones")
                .category("Audio")
                .price(new BigDecimal("398.00"))
                .rating(4.9)
                .stock(25)
                .imageUrl("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500")
                .description("Industry-leading noise canceling with Auto NC Optimizer.")
                .build(),
            Product.builder()
                .id("PROD-005")
                .name("Apple iPad Air (5th Gen)")
                .category("Computers")
                .price(new BigDecimal("599.00"))
                .rating(4.8)
                .stock(12)
                .imageUrl("https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=500")
                .description("Supercharged by the Apple M1 chip. 10.9-inch Liquid Retina display.")
                .build(),
            Product.builder()
                .id("PROD-006")
                .name("Logitech MX Master 3S Wireless Mouse")
                .category("Computers")
                .price(new BigDecimal("99.99"))
                .rating(4.7)
                .stock(50)
                .imageUrl("https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=500")
                .description("Performance wireless mouse with 8K DPI track-on-glass sensor.")
                .build()
        );

        initialProducts.forEach(p -> productCatalog.put(p.getId(), p));
        log.info("Initialized product catalog with {} items", productCatalog.size());
    }

    public List<Product> getAllProducts(String query, String category) {
        String currentTraceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "none";
        log.info("Fetching products catalog. Query='{}', Category='{}' [traceId={}]", query, category, currentTraceId);

        return productCatalog.values().stream()
            .filter(p -> (query == null || query.isBlank() || p.getName().toLowerCase().contains(query.toLowerCase())))
            .filter(p -> (category == null || category.isBlank() || category.equalsIgnoreCase("All") || p.getCategory().equalsIgnoreCase(category)))
            .collect(Collectors.toList());
    }

    public Optional<Product> getProductById(String id) {
        log.info("Fetching product details for ID: {}", id);
        Product product = productCatalog.get(id);
        if (product != null && product.getStock() <= 5) {
            log.warn("Low inventory alert for Product ID {}: Only {} items left in stock!", id, product.getStock());
        }
        return Optional.ofNullable(product);
    }

    public synchronized boolean deductStock(String productId, int quantity) {
        Product product = productCatalog.get(productId);
        if (product == null) {
            log.error("Stock deduction failed: Product ID {} not found!", productId);
            return false;
        }
        if (product.getStock() < quantity) {
            log.warn("Stock deduction rejected for Product '{}' (ID: {}). Requested: {}, Available: {}",
                    product.getName(), productId, quantity, product.getStock());
            return false;
        }
        product.setStock(product.getStock() - quantity);
        log.info("Stock updated for Product '{}' (ID: {}). Remaining stock: {}",
                product.getName(), productId, product.getStock());
        return true;
    }
}
