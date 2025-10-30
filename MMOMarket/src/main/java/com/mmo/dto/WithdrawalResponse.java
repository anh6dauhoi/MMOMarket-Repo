package com.mmo.dto;

import com.mmo.entity.Withdrawal;

public record WithdrawalResponse(Long id, Long sellerId, Long amount, String status, String proofFile) {
    public static WithdrawalResponse from(Withdrawal w) {
        return new WithdrawalResponse(
                w.getId(),
                w.getSeller() != null ? w.getSeller().getId() : null,
                w.getAmount(),
                w.getStatus(),
                w.getProofFile()
        );
    }
}
