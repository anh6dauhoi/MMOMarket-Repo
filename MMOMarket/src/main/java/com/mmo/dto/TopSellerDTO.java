package com.mmo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopSellerDTO {
    private String sellerName;
    private Long revenue;
    private Double avgRating;
    private Long transactionCount;
}

