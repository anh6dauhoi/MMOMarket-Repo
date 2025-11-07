package com.mmo.repository;

import com.mmo.entity.Product;
import com.mmo.entity.Review;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    // average rating for a seller: go via review.product.seller.id
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.seller.id = :sellerId AND r.isDelete = false")
    Double getAverageRatingBySeller(@Param("sellerId") Long sellerId);

    // average rating for a product
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.isDelete = false")
    Double getAverageRatingByProduct(@Param("productId") Long productId);

    // Fetch newest reviews for a product
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.isDelete = false ORDER BY r.createdAt DESC")
    List<Review> findByProductIdAndIsDeleteFalseOrderByCreatedAtDesc(@Param("productId") Long productId);

    List<Review> findByProduct(Product product);
    List<Review> findByProduct_Id(Long productId);
    List<Review> findByUser(User user);
    List<Review> findByUser_Id(Long userId);

    // NEW: check if user already reviewed this product (soft-deleted excluded is handled at controller if needed)
    boolean existsByUser_IdAndProduct_Id(Long userId, Long productId);
    long countByUser_IdAndProduct_Id(Long userId, Long productId);

    // NEW: find latest review by this user for a product (not deleted)
    Optional<Review> findFirstByUser_IdAndProduct_IdAndIsDeleteFalseOrderByCreatedAtDesc(Long userId, Long productId);
}
