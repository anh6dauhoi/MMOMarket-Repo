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

    public List<ProductVariant> findAll() {
        return productVariantRepository.findByIsDeleteFalse();
    }

    public List<ProductVariant> findByProductId(Long productId) {
        return productVariantRepository.findByProductIdAndIsDeleteFalse(productId);
    }

    public ProductVariant findById(Long id) {
        return productVariantRepository.findByIdAndIsDeleteFalse(id)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));
    }

    public ProductVariant save(ProductVariant variant) {
        return productVariantRepository.save(variant);
    }

    public void softDelete(Long id, Long deletedBy) {
        ProductVariant variant = findById(id);
        variant.setDelete(true);
        variant.setDeletedBy(deletedBy);
        variant.setUpdatedAt(new Date());
        productVariantRepository.save(variant);
    }

    public void updateStock(Long variantId, int quantity) {
        ProductVariant variant = findById(variantId);
        variant.setStock(variant.getStock() - quantity);
        if (variant.getStock() <= 0) {
            variant.setStatus("Inactive");
        }
        productVariantRepository.save(variant);
    }
}