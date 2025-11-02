package com.mmo.service;

import com.mmo.entity.Category;
import com.mmo.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public List<Category> findAll() {
        return categoryRepository.findByIsDeleteFalse();
    }

    public Category findById(Long id) {
        return categoryRepository.findByIdAndIsDeleteFalse(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    public Category findByName(String name) {
        return categoryRepository.findByNameAndIsDeleteFalse(name)
                .orElse(null);
    }

    public List<Category> search(String keyword) {
        return categoryRepository.findByNameContainingIgnoreCaseAndIsDeleteFalse(keyword);
    }

    public Category save(Category category) {
        return categoryRepository.save(category);
    }

    public Category create(Category category) {
        // Kiểm tra trùng tên
        if (categoryRepository.existsByNameAndIsDeleteFalse(category.getName())) {
            throw new RuntimeException("Category name already exists");
        }
        return categoryRepository.save(category);
    }

    public Category update(Long id, Category categoryDetails) {
        Category category = findById(id);

        // Kiểm tra trùng tên (trừ chính nó)
        Category existingCategory = findByName(categoryDetails.getName());
        if (existingCategory != null && !existingCategory.getId().equals(id)) {
            throw new RuntimeException("Category name already exists");
        }

        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        category.setUpdatedAt(new Date());

        return categoryRepository.save(category);
    }

    public void softDelete(Long id, Long deletedBy) {
        Category category = findById(id);
        category.setDelete(true);
        category.setDeletedBy(deletedBy);
        category.setUpdatedAt(new Date());
        categoryRepository.save(category);
    }

    public boolean existsByName(String name) {
        return categoryRepository.existsByNameAndIsDeleteFalse(name);
    }
}