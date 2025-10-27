package com.mmo.repository;

import com.mmo.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    Optional<Wishlist> findByUserIdAndProductId(Long userId, Long productId);
    List<Wishlist> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}

