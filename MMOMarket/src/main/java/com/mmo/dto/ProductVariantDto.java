package com.mmo.dto;

public class ProductVariantDto {
    private Long id;
    private String variantName;
    private Long price;
    private Long stock;
    private Long sold;
    private boolean isDelete;

    public ProductVariantDto(Long id, String variantName, Long price, Long stock, Long sold, boolean isDelete) {
        this.id = id;
        this.variantName = variantName;
        this.price = price;
        this.stock = stock;
        this.sold = sold;
        this.isDelete = isDelete;
    }

    // getters (used by Thymeleaf)
    public Long getId() { return id; }
    public String getVariantName() { return variantName; }
    public Long getPrice() { return price; }
    public Long getStock() { return stock; }
    public Long getSold() { return sold; }
    public boolean isDelete() { return isDelete; }
}
