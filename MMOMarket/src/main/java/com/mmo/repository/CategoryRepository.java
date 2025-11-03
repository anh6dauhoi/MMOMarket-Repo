package com.mmo.repository;

import com.mmo.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query(value = "SELECT c.* FROM Categories c LEFT JOIN Products p ON c.id = p.category_id WHERE c.isDelete = false GROUP BY c.id, c.name, c.description " +
            "ORDER BY COUNT(p.id) DESC LIMIT 4", nativeQuery = true)
    List<Category> findPopularCategories();

    List<Category> findAll();

    // New methods for admin management
    Page<Category> findByIsDeleteOrderByCreatedAtDesc(boolean isDelete, Pageable pageable);

    Page<Category> findByNameContainingIgnoreCaseAndIsDeleteOrderByCreatedAtDesc(String name, boolean isDelete, Pageable pageable);

    Optional<Category> findByIdAndIsDelete(Long id, boolean isDelete);

    boolean existsByNameIgnoreCaseAndIsDelete(String name, boolean isDelete);

    boolean existsByNameIgnoreCaseAndIsDeleteAndIdNot(String name, boolean isDelete, Long id);
}
