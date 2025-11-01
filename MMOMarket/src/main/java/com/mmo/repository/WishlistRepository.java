package com.mmo.repository;

import com.mmo.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    Optional<Wishlist> findByUserIdAndProductId(Long userId, Long productId);
    List<Wishlist> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Page<Wishlist> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
