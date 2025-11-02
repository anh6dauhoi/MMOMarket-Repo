package com.mmo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mmo.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Find all non-deleted categories with pagination
    Page<Category> findByIsDeleteOrderByCreatedAtDesc(boolean isDelete, Pageable pageable);

    // Find by name (case-insensitive) and not deleted
    Page<Category> findByNameContainingIgnoreCaseAndIsDeleteOrderByCreatedAtDesc(String name, boolean isDelete, Pageable pageable);

    // Find by id and not deleted
    Optional<Category> findByIdAndIsDelete(Long id, boolean isDelete);

    // Check if category name exists (case-insensitive, excluding specific id)
    boolean existsByNameIgnoreCaseAndIsDeleteAndIdNot(String name, boolean isDelete, Long id);

    // Check if category name exists (case-insensitive)
    boolean existsByNameIgnoreCaseAndIsDelete(String name, boolean isDelete);

    // Count categories by delete status
    long countByIsDelete(boolean isDelete);
}
