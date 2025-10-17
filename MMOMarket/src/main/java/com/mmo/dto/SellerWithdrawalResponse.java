package com.mmo.dto;

import com.mmo.entity.Withdrawal;

public record SellerWithdrawalResponse(Long id, Long amount, String status, Long bankInfoId) {
    public static SellerWithdrawalResponse from(Withdrawal w) {
        return new SellerWithdrawalResponse(
                w.getId(),
                w.getAmount(),
                w.getStatus(),
                w.getBankInfo() != null ? w.getBankInfo().getId() : null
        );
    }
}
