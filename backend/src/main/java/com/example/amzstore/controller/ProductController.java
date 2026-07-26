package com.example.amzstore.controller;

import com.example.amzstore.dto.ApiResponse;
import com.example.amzstore.model.Product;
import com.example.amzstore.service.ProductService;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final Tracer tracer;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category) {
        
        List<Product> products = productService.getAllProducts(query, category);
        
        String traceId = getTraceId();
        String spanId = getSpanId();

        return ResponseEntity.ok(ApiResponse.<List<Product>>builder()
                .success(true)
                .message("Products retrieved successfully")
                .data(products)
                .traceId(traceId)
                .spanId(spanId)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable String id) {
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(ApiResponse.<Product>builder()
                        .success(true)
                        .message("Product details retrieved")
                        .data(product)
                        .traceId(getTraceId())
                        .spanId(getSpanId())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    private String getTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "none";
    }

    private String getSpanId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().spanId() : "none";
    }
}
