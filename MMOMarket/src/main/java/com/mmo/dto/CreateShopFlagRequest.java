package com.mmo.dto;

public record CreateShopFlagRequest(
    Long shopId,
    Long relatedComplaintId,
    String reason,
    String flagLevel  // "WARNING", "SEVERE", or "BANNED"
) {
}

