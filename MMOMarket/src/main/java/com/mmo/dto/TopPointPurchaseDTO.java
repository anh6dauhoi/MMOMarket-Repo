package com.mmo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopPointPurchaseDTO {
    private String buyerName;
    private Long amount;
    private String date;
}

