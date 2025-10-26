package com.mmo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mmo.dto.ShopResponse;
import com.mmo.dto.UpdateCommissionRequest;

public interface ShopService {
    Page<ShopResponse> getAllShops(Pageable pageable);
    Page<ShopResponse> searchShops(String keyword, Pageable pageable);
    void updateCommission(Long shopId, UpdateCommissionRequest request, Long userId);
    void deleteShop(Long shopId, Long userId);
    Page<ShopResponse> getDeletedShops(Pageable pageable);
    void restoreShop(Long shopId);
}
