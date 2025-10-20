package com.mmo.dto;

public record CoinDepositDetailResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        Long amount,
        Long coinsAdded,
        String status,
        Long sepayTransactionId,
        String sepayReferenceCode,
        String gateway,
        String transactionDate,
        String content,
        String createdAt,
        String updatedAt
) {}

