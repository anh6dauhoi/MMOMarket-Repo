package com.mmo.dto;

public record AdminTransactionDetailResponse(
        Long id,
        Long orderId,
        String orderRequestId,
        String orderStatus,
        String errorMessage,
        String processedAt,
        String status,
        Long amount,
        Long commission,
        Long coinAdmin,
        Long coinSeller,
        Long quantity,
        String productName,
        String variantName,
        Long customerId,
        String customerName,
        String customerEmail,
        Long sellerId,
        String sellerName,
        String sellerEmail,
        String escrowReleaseDate,
        String createdAt,
        String updatedAt
) {}
