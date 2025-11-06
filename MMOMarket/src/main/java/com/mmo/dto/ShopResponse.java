package com.mmo.dto;

import java.math.BigDecimal;
import java.util.Date;

import com.mmo.entity.ShopInfo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopResponse {
    private Long id;
    private String shopName;
    private Long sellerId;
    private String sellerEmail;
    private String sellerName;
    private String status;
    private Long productCount;
    private Double rating;
    private BigDecimal commission;
    private Short shopLevel;
    private String tierName;
    private String signedContract;
    private Date createdAt;
    private String deletedBy;
    private boolean isDelete;

    public static ShopResponse fromEntity(ShopInfo shop, Long productCount, Double rating) {
        ShopResponse response = new ShopResponse();
        response.setId(shop.getId());
        response.setShopName(shop.getShopName());

        if (shop.getUser() != null) {
            response.setSellerId(shop.getUser().getId());
            response.setSellerEmail(shop.getUser().getEmail());
            response.setSellerName(shop.getUser().getFullName());
            response.setStatus(shop.getUser().getShopStatus());
        }

        response.setProductCount(productCount);
        response.setRating(rating);
        response.setCommission(shop.getCommission());
        response.setShopLevel(shop.getShopLevel());
        response.setTierName(com.mmo.util.TierNameUtil.getTierName(shop.getShopLevel()));
        response.setCreatedAt(shop.getCreatedAt());
        response.setDelete(shop.isDelete());

        if (shop.getDeletedBy() != null) {
            response.setDeletedBy(shop.getDeletedBy().getEmail());
        }

        return response;
    }
}
