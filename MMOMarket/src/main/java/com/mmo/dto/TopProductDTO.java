package com.mmo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TopProductDTO {
    // Common fields
    private Long id;
    private String productName;
    private Long soldCount;
    private Long revenue;

    // Seller-specific fields
    private String productImage;
    private Long salesCount;
    private Double salesPercentage;

    // Constructor for Admin Dashboard (DashboardService)
    // Uses: productName, soldCount, revenue
    public TopProductDTO(String productName, Long soldCount, Long revenue) {
        this.productName = productName;
        this.soldCount = soldCount;
        this.revenue = revenue;
        // Set salesCount as alias for soldCount
        this.salesCount = soldCount;
    }

    // Constructor for Seller Dashboard (SellerController)
    // Uses: id, name (as productName), image (as productImage), salesCount, percentage (as salesPercentage)
    public TopProductDTO(Long id, String name, String image, Long salesCount, Double percentage) {
        this.id = id;
        this.productName = name;  // map name -> productName
        this.productImage = image;  // map image -> productImage
        this.salesCount = salesCount;
        this.salesPercentage = percentage;  // map percentage -> salesPercentage
        this.soldCount = salesCount;  // set soldCount as alias
    }

    // Full constructor for maximum flexibility
    public TopProductDTO(Long id, String productName, String productImage,
                         Long soldCount, Long revenue, Long salesCount, Double salesPercentage) {
        this.id = id;
        this.productName = productName;
        this.productImage = productImage;
        this.soldCount = soldCount;
        this.revenue = revenue;
        this.salesCount = salesCount;
        this.salesPercentage = salesPercentage;
    }
}

