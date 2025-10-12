package com.mmo.repository;

import com.mmo.entity.SellerRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerRegistrationRepository extends JpaRepository<SellerRegistration, Long> {
    Optional<SellerRegistration> findByUserId(Long userId);
    Page<SellerRegistration> findByStatus(String status, Pageable pageable);
    boolean existsByShopNameIgnoreCaseAndStatusIn(String shopName, List<String> statuses);
}
