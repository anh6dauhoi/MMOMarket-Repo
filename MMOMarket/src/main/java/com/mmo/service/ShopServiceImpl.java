package com.mmo.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mmo.dto.ShopResponse;
import com.mmo.dto.UpdateCommissionRequest;
import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.repository.SellerRegistrationRepository;
import com.mmo.repository.ShopRepository;
import com.mmo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final SellerRegistrationRepository sellerRegistrationRepository;

    @Override
    public Page<ShopResponse> getAllShops(Pageable pageable) {
        Page<ShopInfo> shops = shopRepository.findAllActiveShops(pageable);
        return shops.map(shop -> {
            ShopResponse response = ShopResponse.fromEntity(shop, 0L, 0.0);
            // Get signed contract from SellerRegistration
            if (shop.getUser() != null) {
                sellerRegistrationRepository.findByUserId(shop.getUser().getId())
                    .ifPresent(reg -> response.setSignedContract(reg.getSignedContract()));
            }
            return response;
        });
    }

    @Override
    public Page<ShopResponse> searchShops(String keyword, Pageable pageable) {
        Page<ShopInfo> shops = shopRepository.searchShops(keyword, pageable);
        return shops.map(shop -> {
            ShopResponse response = ShopResponse.fromEntity(shop, 0L, 0.0);
            // Get signed contract from SellerRegistration
            if (shop.getUser() != null) {
                sellerRegistrationRepository.findByUserId(shop.getUser().getId())
                    .ifPresent(reg -> response.setSignedContract(reg.getSignedContract()));
            }
            return response;
        });
    }

    @Override
    @Transactional
    public void updateCommission(Long shopId, UpdateCommissionRequest request, Long userId) {
        ShopInfo shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new RuntimeException("Shop not found"));

        if (shop.isDelete()) {
            throw new RuntimeException("Cannot update commission for deleted shop");
        }

        if (request.getCommission() == null) {
            throw new RuntimeException("Commission rate is required");
        }

        BigDecimal commission = request.getCommission();
        if (commission.compareTo(BigDecimal.ZERO) < 0 || commission.compareTo(new BigDecimal("100.00")) > 0) {
            throw new RuntimeException("Commission rate must be between 0 and 100");
        }

        shop.setCommission(commission);


        shopRepository.save(shop);
    }

    @Override
    @Transactional
    public void deleteShop(Long shopId, Long userId) {
        ShopInfo shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new RuntimeException("Shop not found"));

        if (shop.isDelete()) {
            throw new RuntimeException("Shop is already deleted");
        }

        shop.setDelete(true);

        User deletedBy = userRepository.findById(userId).orElse(null);
        if (deletedBy != null) {
            shop.setDeletedBy(deletedBy);
        }

        // Update user's shop status to Inactive
        User user = shop.getUser();
        if (user != null) {
            user.setShopStatus("Inactive");
            userRepository.save(user);
        }

        shopRepository.save(shop);
    }

    @Override
    public Page<ShopResponse> getDeletedShops(Pageable pageable) {
        Page<ShopInfo> shops = shopRepository.findDeletedShops(pageable);
        return shops.map(shop -> {
            ShopResponse response = ShopResponse.fromEntity(shop, 0L, 0.0);
            // Get signed contract from SellerRegistration
            if (shop.getUser() != null) {
                sellerRegistrationRepository.findByUserId(shop.getUser().getId())
                    .ifPresent(reg -> response.setSignedContract(reg.getSignedContract()));
            }
            return response;
        });
    }

    @Override
    @Transactional
    public void restoreShop(Long shopId) {
        ShopInfo shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new RuntimeException("Shop not found"));

        if (!shop.isDelete()) {
            throw new RuntimeException("Shop is not deleted");
        }

        shop.setDelete(false);
        shop.setDeletedBy(null);

        // Update user's shop status to Active
        User user = shop.getUser();
        if (user != null) {
            user.setShopStatus("Active");
            userRepository.save(user);
        }

        shopRepository.save(shop);
    }
}
