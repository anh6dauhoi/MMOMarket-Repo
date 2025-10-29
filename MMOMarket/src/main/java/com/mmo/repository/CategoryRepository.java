package com.mmo.repository;
import com.mmo.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query(value = "SELECT c.* FROM Categories c LEFT JOIN Products p ON c.id = p.category_id WHERE c.isDelete = false GROUP BY c.id, c.name, c.description " +
            "ORDER BY COUNT(p.id) DESC LIMIT 4", nativeQuery = true)
    List<Category> findPopularCategories();

    List<Category> findAll();

}