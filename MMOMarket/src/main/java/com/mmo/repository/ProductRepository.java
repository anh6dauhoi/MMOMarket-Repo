package com.mmo.repository;

import com.mmo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // ...existing code...
}

