package com.mmo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mmo.dto.CreateCategoryRequest;
import com.mmo.dto.UpdateCategoryRequest;
import com.mmo.entity.Category;

public interface CategoryService {
    /**
     * Get all categories (not deleted) with pagination
     */
    Page<Category> getAllCategories(Pageable pageable);

    /**
     * Search categories by name with pagination
     */
    Page<Category> searchCategories(String search, Pageable pageable);

    /**
     * Get category by ID
     */
    Category getCategoryById(Long id);

    /**
     * Create new category
     */
    Category createCategory(CreateCategoryRequest request, Long createdBy);

    /**
     * Update existing category
     */
    Category updateCategory(Long id, UpdateCategoryRequest request);

    /**
     * Soft delete category
     */
    void deleteCategory(Long id, Long deletedBy);

    /**
     * Restore deleted category
     */
    Category restoreCategory(Long id);

    /**
     * Toggle category status (Active/Inactive)
     */
    Category toggleCategoryStatus(Long id);

    /**
     * Get all deleted categories with pagination
     */
    Page<Category> getDeletedCategories(Pageable pageable);

    /**
     * Search deleted categories
     */
    Page<Category> searchDeletedCategories(String search, Pageable pageable);
}
