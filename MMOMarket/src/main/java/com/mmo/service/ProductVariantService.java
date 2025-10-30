package com.mmo.service;

import com.mmo.entity.ProductVariant;
import com.mmo.repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class ProductVariantService {

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductVariantAccountService productVariantAccountService;

    public List<ProductVariant> findAll() {
        List<ProductVariant> variants = productVariantRepository.findByIsDeleteFalse();
        // Set stock cho mỗi variant
        for (ProductVariant variant : variants) {
            variant.setStock(productVariantAccountService.countAvailableAccounts(variant.getId()));
        }
        return variants;
    }

    public List<ProductVariant> findByProductId(Long productId) {
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(productId);
        // Set stock cho mỗi variant
        for (ProductVariant variant : variants) {
            variant.setStock(productVariantAccountService.countAvailableAccounts(variant.getId()));
        }
        return variants;
    }

    public ProductVariant findById(Long id) {
        ProductVariant variant = productVariantRepository.findByIdAndIsDeleteFalse(id)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));
        // Set stock
        variant.setStock(productVariantAccountService.countAvailableAccounts(id));
        return variant;
    }

    public ProductVariant save(ProductVariant variant) {
        return productVariantRepository.save(variant);
    }

    public void softDelete(Long id, Long deletedBy) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));
        variant.setDelete(true);
        variant.setDeletedBy(deletedBy);
        variant.setUpdatedAt(new Date());
        productVariantRepository.save(variant);
    }

    /**
     * Cập nhật status của variant dựa trên số account available
     * Gọi method này sau khi thêm/xóa accounts
     *
     * ALIAS: updateStock() - để tương thích với code cũ
     */
    public void updateStock(Long variantId) {
        updateVariantStatus(variantId);
    }

    /**
     * Cập nhật status của variant dựa trên số account available
     * Gọi method này sau khi thêm/xóa accounts
     */
    public void updateVariantStatus(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        // Đếm số account Available
        long availableCount = productVariantAccountService.countAvailableAccounts(variantId);

        variant.setUpdatedAt(new Date());

        // Cập nhật status dựa trên stock
        if (availableCount > 0) {
            variant.setStatus("Active");
        } else {
            variant.setStatus("Inactive");
        }

        productVariantRepository.save(variant);
    }

    /**
     * Lấy số lượng stock còn lại (số account Available)
     */
    public long getAvailableStock(Long variantId) {
        return productVariantAccountService.countAvailableAccounts(variantId);
    }
}