package com.mmo.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mmo.dto.CreateCategoryRequest;
import com.mmo.dto.UpdateCategoryRequest;
import com.mmo.entity.Category;
import com.mmo.repository.CategoryRepository;
import com.mmo.service.CategoryService;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    public Page<Category> getAllCategories(Pageable pageable) {
        return categoryRepository.findByIsDeleteOrderByCreatedAtDesc(false, pageable);
    }

    @Override
    public Page<Category> searchCategories(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return getAllCategories(pageable);
        }
        return categoryRepository.findByNameContainingIgnoreCaseAndIsDeleteOrderByCreatedAtDesc(
                search.trim(), false, pageable);
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryRepository.findByIdAndIsDelete(id, false)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));
    }

    @Override
    @Transactional
    public Category createCategory(CreateCategoryRequest request, Long createdBy) {
        // Validate input
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }

        String name = request.getName().trim();

        // Validate name length
        if (name.length() < 2) {
            throw new IllegalArgumentException("Category name must be at least 2 characters");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Category name is too long (max 100 characters)");
        }

        // Check if category name already exists
        if (categoryRepository.existsByNameIgnoreCaseAndIsDelete(name, false)) {
            throw new IllegalArgumentException("Category name already exists: " + name);
        }

        // Validate type
        String type = request.getType();
        if (type == null || type.trim().isEmpty()) {
            type = "Common";
        } else {
            type = type.trim();
            if (!type.equalsIgnoreCase("Common") && !type.equalsIgnoreCase("Warning")) {
                throw new IllegalArgumentException("Category type must be either 'Common' or 'Warning'");
            }
        }

        // Create category
        Category category = new Category();
        category.setName(name);
        category.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        category.setType(type);
        category.setCreatedBy(createdBy);
        category.setDelete(false);

        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category updateCategory(Long id, UpdateCategoryRequest request) {
        // Find existing category
        Category category = getCategoryById(id);

        // Validate and update name
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String name = request.getName().trim();

            // Validate name length
            if (name.length() < 2) {
                throw new IllegalArgumentException("Category name must be at least 2 characters");
            }
            if (name.length() > 100) {
                throw new IllegalArgumentException("Category name is too long (max 100 characters)");
            }

            // Check if new name already exists (excluding current category)
            if (!name.equalsIgnoreCase(category.getName()) &&
                categoryRepository.existsByNameIgnoreCaseAndIsDeleteAndIdNot(name, false, id)) {
                throw new IllegalArgumentException("Category name already exists: " + name);
            }

            category.setName(name);
        }

        // Update description
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().trim());
        }

        // Update type
        if (request.getType() != null && !request.getType().trim().isEmpty()) {
            String type = request.getType().trim();
            if (!type.equalsIgnoreCase("Common") && !type.equalsIgnoreCase("Warning")) {
                throw new IllegalArgumentException("Category type must be either 'Common' or 'Warning'");
            }
            category.setType(type);
        }

        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id, Long deletedBy) {
        Category category = getCategoryById(id);
        category.setDelete(true);
        category.setDeletedBy(deletedBy);
        categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category restoreCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));

        if (!category.isDelete()) {
            throw new IllegalArgumentException("Category is not deleted");
        }

        // Check if name conflicts with existing active category
        if (categoryRepository.existsByNameIgnoreCaseAndIsDelete(category.getName(), false)) {
            throw new IllegalArgumentException("Cannot restore: Category name already exists - " + category.getName());
        }

        category.setDelete(false);
        category.setDeletedBy(null);
        return categoryRepository.save(category);
    }

    @Override
    public Page<Category> getDeletedCategories(Pageable pageable) {
        return categoryRepository.findByIsDeleteOrderByCreatedAtDesc(true, pageable);
    }

    @Override
    public Page<Category> searchDeletedCategories(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return getDeletedCategories(pageable);
        }
        return categoryRepository.findByNameContainingIgnoreCaseAndIsDeleteOrderByCreatedAtDesc(
                search.trim(), true, pageable);
    }
}
