package com.mmo.service;
import com.mmo.entity.Category;
import com.mmo.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public List<Category> getPopularCategories() {
        return categoryRepository.findPopularCategories();
    }

    public List<Category> findAll(){
        return categoryRepository.findAll();
    }

    public Optional<Category> findById(Long id){
        return  categoryRepository.findById(id);
    }
}
