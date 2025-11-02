package com.mmo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mmo.entity.Blog;

@Repository
public interface BlogRepository extends JpaRepository<Blog, Long> {

    // Find all non-deleted blogs with pagination
    Page<Blog> findByIsDeleteOrderByCreatedAtDesc(boolean isDelete, Pageable pageable);

    // Find all visible (status = true) and non-deleted blogs with pagination
    Page<Blog> findByStatusAndIsDeleteOrderByCreatedAtDesc(boolean status, boolean isDelete, Pageable pageable);

    // Search blogs by title (case-insensitive) and not deleted
    @Query("SELECT b FROM Blog b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')) AND b.isDelete = :isDelete ORDER BY b.createdAt DESC")
    Page<Blog> searchBlogsByTitle(@Param("search") String search, @Param("isDelete") boolean isDelete, Pageable pageable);

    // Search visible blogs by title (case-insensitive), status = true and not deleted
    @Query("SELECT b FROM Blog b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')) AND b.status = :status AND b.isDelete = :isDelete ORDER BY b.createdAt DESC")
    Page<Blog> searchVisibleBlogsByTitle(@Param("search") String search, @Param("status") boolean status, @Param("isDelete") boolean isDelete, Pageable pageable);

    // Find by id and not deleted
    Optional<Blog> findByIdAndIsDelete(Long id, boolean isDelete);

    // Find by id, status and not deleted
    Optional<Blog> findByIdAndStatusAndIsDelete(Long id, boolean status, boolean isDelete);

    // Count blogs by delete status
    long countByIsDelete(boolean isDelete);

    // Count visible blogs by status and delete status
    long countByStatusAndIsDelete(boolean status, boolean isDelete);

    // Count comments for a blog
    @Query("SELECT COUNT(c) FROM BlogComment c WHERE c.blog.id = :blogId")
    long countCommentsByBlogId(@Param("blogId") Long blogId);
}
