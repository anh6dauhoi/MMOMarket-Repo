package com.mmo.repository;

import com.mmo.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByIsDeleteFalse();

    Optional<Category> findByIdAndIsDeleteFalse(Long id);

    Optional<Category> findByNameAndIsDeleteFalse(String name);

    List<Category> findByNameContainingIgnoreCaseAndIsDeleteFalse(String keyword);

    boolean existsByNameAndIsDeleteFalse(String name);
}