package com.mmo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopBuyerDTO {
    private String buyerName;
    private Long totalSpent;
    private Long transactionCount;
}

