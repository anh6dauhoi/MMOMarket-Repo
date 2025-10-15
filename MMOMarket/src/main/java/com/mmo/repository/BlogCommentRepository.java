package com.mmo.repository;

import com.mmo.entity.BlogComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogCommentRepository extends JpaRepository<BlogComment, Long> {
    // ...existing code...
}

