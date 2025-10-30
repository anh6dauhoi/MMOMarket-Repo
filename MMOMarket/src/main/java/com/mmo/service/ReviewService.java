package com.mmo.service;

import com.mmo.entity.Review;
import com.mmo.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    // Lấy tất cả reviews
    public List<Review> findAll() {
        return reviewRepository.findByIsDeleteFalse();
    }

    // Lấy review theo ID
    public Review findById(Long id) {
        return reviewRepository.findByIdAndIsDeleteFalse(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));
    }

    // Lấy reviews của một sản phẩm
    public List<Review> findByProductId(Long productId) {
        return reviewRepository.findByProductIdAndIsDeleteFalseOrderByCreatedAtDesc(productId);
    }

    // Lấy reviews của một user
    public List<Review> findByUserId(Long userId) {
        return reviewRepository.findByUserIdAndIsDeleteFalse(userId);
    }

    // Lấy reviews theo rating
    public List<Review> findByProductIdAndRating(Long productId, Integer rating) {
        return reviewRepository.findByProductIdAndRatingAndIsDeleteFalse(productId, rating);
    }

    // Tính điểm trung bình của sản phẩm
    public double getAverageRating(Long productId) {
        Double avgRating = reviewRepository.getAverageRatingByProductId(productId);
        return avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
    }

    // Đếm tổng số reviews của sản phẩm
    public long getTotalReviews(Long productId) {
        return reviewRepository.countByProductIdAndIsDeleteFalse(productId);
    }

    // Lấy thống kê reviews theo rating (1-5 sao)
    public Map<Integer, Long> getReviewStatsByRating(Long productId) {
        Map<Integer, Long> stats = new HashMap<>();
        // Khởi tạo với 0 cho mỗi rating
        for (int i = 1; i <= 5; i++) {
            stats.put(i, 0L);
        }

        List<Object[]> results = reviewRepository.countReviewsByRating(productId);
        for (Object[] result : results) {
            Integer rating = (Integer) result[0];
            Long count = (Long) result[1];
            stats.put(rating, count);
        }

        return stats;
    }

    // Kiểm tra user đã review sản phẩm chưa
    public boolean hasUserReviewedProduct(Long productId, Long userId) {
        return reviewRepository.existsByProductIdAndUserIdAndIsDeleteFalse(productId, userId);
    }

    // Lấy review của user cho sản phẩm
    public Review getUserReviewForProduct(Long productId, Long userId) {
        return reviewRepository.findByProductIdAndUserIdAndIsDeleteFalse(productId, userId)
                .orElse(null);
    }

    // Lưu review mới
    public Review save(Review review) {
        if (review.getCreatedAt() == null) {
            review.setCreatedAt(new Date());
        }
        review.setUpdatedAt(new Date());
        return reviewRepository.save(review);
    }

    // Cập nhật review
    public Review update(Long id, Review reviewDetails, Long userId) {
        Review existingReview = findById(id);

        // Kiểm tra quyền sở hữu
        if (!existingReview.getUser().getId().equals(userId)) {
            throw new RuntimeException("You can only update your own review");
        }

        existingReview.setRating(reviewDetails.getRating());
        existingReview.setComment(reviewDetails.getComment());
        existingReview.setUpdatedAt(new Date());

        return reviewRepository.save(existingReview);
    }

    // Xóa mềm review
    public void softDelete(Long id, Long deletedBy) {
        Review review = findById(id);
        review.setDelete(true);
        review.setDeletedBy(deletedBy);
        review.setUpdatedAt(new Date());
        reviewRepository.save(review);
    }

    // Xóa cứng review (chỉ admin)
    public void hardDelete(Long id) {
        reviewRepository.deleteById(id);
    }
}