package com.mmo.repository;

import com.mmo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByIsDeleteFalse();

    List<Product> findBySellerIdAndIsDeleteFalse(Long sellerId);

    List<Product> findBySellerIdAndCategoryIdAndIsDeleteFalse(Long sellerId, Long categoryId);

    List<Product> findBySellerIdAndNameContainingIgnoreCaseAndIsDeleteFalse(Long sellerId, String keyword);

    List<Product> findByCategoryIdAndIsDeleteFalse(Long categoryId);

    Optional<Product> findByIdAndIsDeleteFalse(Long id);

    long countBySellerIdAndIsDeleteFalse(Long sellerId);
}