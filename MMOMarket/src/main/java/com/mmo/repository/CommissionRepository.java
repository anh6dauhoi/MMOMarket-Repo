package com.mmo.repository;

import com.mmo.entity.Commission;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommissionRepository extends JpaRepository<Commission, Long> {
    // ...existing code...
    Optional<Commission> findByUser(User user);

    Optional<Commission> findByUser_Id(Long userId);
}

