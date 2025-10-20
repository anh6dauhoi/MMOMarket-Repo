package com.mmo.dto;

public class ProductVariantDto {
    private Long id;
    private String variantName;
    private Long price;
    private Long stock;

    public ProductVariantDto(Long id, String variantName, Long price, Long stock) {
        this.id = id;
        this.variantName = variantName;
        this.price = price;
        this.stock = stock;
    }

    // getters (used by Thymeleaf)
    public Long getId() { return id; }
    public String getVariantName() { return variantName; }
    public Long getPrice() { return price; }
    public Long getStock() { return stock; }
}

