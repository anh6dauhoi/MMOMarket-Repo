package com.mmo.repository;

import com.mmo.entity.Product;
import com.mmo.entity.Review;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    // ...existing code...
    List<Review> findByProduct(Product product);
    List<Review> findByProduct_Id(Long productId);
    List<Review> findByUser(User user);
    List<Review> findByUser_Id(Long userId);
}

