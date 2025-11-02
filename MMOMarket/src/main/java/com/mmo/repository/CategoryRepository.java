package com.mmo.repository;
<<<<<<< HEAD

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mmo.entity.Category;
=======
import com.mmo.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
>>>>>>> main

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

<<<<<<< HEAD
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
=======
    @Query(value = "SELECT c.* FROM Categories c LEFT JOIN Products p ON c.id = p.category_id WHERE c.isDelete = false GROUP BY c.id, c.name, c.description " +
            "ORDER BY COUNT(p.id) DESC LIMIT 4", nativeQuery = true)
    List<Category> findPopularCategories();

    List<Category> findAll();

}
>>>>>>> main
