package com.mmo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTotalsDTO {
    private Long totalTransactions;
    private Long totalRevenue;
    private Long totalUsers;
    private Long pointRevenue;
    private String lastUpdated;
}

