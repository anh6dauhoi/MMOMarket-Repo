package com.mmo.dto;

import com.mmo.entity.ShopFlag;

import java.util.Date;

public record ShopFlagResponse(
    Long id,
    Long shopId,
    String shopName,
    Long adminId,
    String adminName,
    Long relatedComplaintId,
    String reason,
    String flagLevel,
    String status,
    String resolutionNotes,
    Date createdAt,
    Date updatedAt,
    Date resolvedAt
) {
    public static ShopFlagResponse from(ShopFlag flag) {
        return new ShopFlagResponse(
            flag.getId(),
            flag.getShop() != null ? flag.getShop().getId() : null,
            flag.getShop() != null ? flag.getShop().getShopName() : null,
            flag.getAdmin() != null ? flag.getAdmin().getId() : null,
            flag.getAdmin() != null ? flag.getAdmin().getFullName() : null,
            flag.getRelatedComplaint() != null ? flag.getRelatedComplaint().getId() : null,
            flag.getReason(),
            flag.getFlagLevel() != null ? flag.getFlagLevel().name() : null,
            flag.getStatus() != null ? flag.getStatus().name() : null,
            flag.getResolutionNotes(),
            flag.getCreatedAt(),
            flag.getUpdatedAt(),
            flag.getResolvedAt()
        );
    }
}

