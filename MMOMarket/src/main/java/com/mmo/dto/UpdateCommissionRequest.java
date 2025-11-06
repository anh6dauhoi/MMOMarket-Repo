package com.mmo.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class UpdateCommissionRequest {
    private BigDecimal commission;
}