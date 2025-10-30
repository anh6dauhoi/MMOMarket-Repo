package com.mmo.repository;

import com.mmo.entity.Product;
import com.mmo.entity.Review;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Tìm tất cả reviews chưa bị xóa
    List<Review> findByIsDeleteFalse();

    // Tìm review theo ID và chưa bị xóa
    Optional<Review> findByIdAndIsDeleteFalse(Long id);

    // Tìm tất cả reviews của một sản phẩm
    List<Review> findByProductIdAndIsDeleteFalse(Long productId);

    // Tìm tất cả reviews của một user
    List<Review> findByUserIdAndIsDeleteFalse(Long userId);

    // Tìm reviews của một sản phẩm sắp xếp theo ngày tạo mới nhất
    List<Review> findByProductIdAndIsDeleteFalseOrderByCreatedAtDesc(Long productId);

    // Tìm reviews của một sản phẩm theo rating
    List<Review> findByProductIdAndRatingAndIsDeleteFalse(Long productId, Integer rating);

    // Đếm số lượng reviews của một sản phẩm
    long countByProductIdAndIsDeleteFalse(Long productId);

    // Tính điểm trung bình của một sản phẩm
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.isDelete = false")
    Double getAverageRatingByProductId(@Param("productId") Long productId);

    // Đếm số lượng reviews theo từng rating của một sản phẩm
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.isDelete = false GROUP BY r.rating")
    List<Object[]> countReviewsByRating(@Param("productId") Long productId);

    // Kiểm tra user đã review sản phẩm chưa
    boolean existsByProductIdAndUserIdAndIsDeleteFalse(Long productId, Long userId);

    // Tìm review của user cho một sản phẩm cụ thể
    Optional<Review> findByProductIdAndUserIdAndIsDeleteFalse(Long productId, Long userId);

    List<Review> findByProduct(Product product);

    List<Review> findByProduct_Id(Long productId);

    List<Review> findByUser(User user);

    List<Review> findByUser_Id(Long userId);
}