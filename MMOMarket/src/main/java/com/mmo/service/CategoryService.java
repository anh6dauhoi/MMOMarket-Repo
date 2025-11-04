package com.mmo.service;

import com.mmo.dto.CreateCategoryRequest;
import com.mmo.dto.UpdateCategoryRequest;
import com.mmo.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CategoryService {

    // Old methods
    List<Category> getPopularCategories();

    List<Category> findAll();

    Optional<Category> findById(Long id);

    // New methods for admin
    Page<Category> getAllCategories(Pageable pageable);

    Page<Category> searchCategories(String search, Pageable pageable);

    Category getCategoryById(Long id);

    Category createCategory(CreateCategoryRequest request, Long createdBy);

    Category updateCategory(Long id, UpdateCategoryRequest request);

    void deleteCategory(Long id, Long deletedBy);

    Category restoreCategory(Long id);

    Category toggleCategoryStatus(Long id);

    Page<Category> getDeletedCategories(Pageable pageable);

    Page<Category> searchDeletedCategories(String search, Pageable pageable);
}
