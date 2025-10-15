package com.mmo.repository;

import com.mmo.entity.Blog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogRepository extends JpaRepository<Blog, Long> {
    // ...existing code...
}

