package com.example.amzstore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    private String id;
    private String name;
    private String category;
    private BigDecimal price;
    private Double rating;
    private Integer stock;
    private String imageUrl;
    private String description;
}
