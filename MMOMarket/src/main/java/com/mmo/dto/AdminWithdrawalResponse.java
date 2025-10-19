package com.mmo.dto;

import com.mmo.entity.Withdrawal;

public record AdminWithdrawalResponse(Long id, Long sellerId, Long amount, String status, String proofFile) {
    public static AdminWithdrawalResponse from(Withdrawal w) {
        return new AdminWithdrawalResponse(
                w.getId(),
                w.getSeller() != null ? w.getSeller().getId() : null,
                w.getAmount(),
                w.getStatus(),
                w.getProofFile()
        );
    }
}
