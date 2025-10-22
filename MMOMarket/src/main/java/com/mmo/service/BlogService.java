package com.mmo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mmo.dto.CreateBlogRequest;
import com.mmo.dto.UpdateBlogRequest;
import com.mmo.entity.Blog;

public interface BlogService {

    /**
     * Get all non-deleted blogs with pagination
     */
    Page<Blog> getAllBlogs(Pageable pageable);

    /**
     * Search blogs by title
     */
    Page<Blog> searchBlogs(String search, Pageable pageable);

    /**
     * Get blog by ID
     */
    Blog getBlogById(Long id);

    /**
     * Create new blog
     */
    Blog createBlog(CreateBlogRequest request, Long authorId);

    /**
     * Update existing blog
     */
    Blog updateBlog(Long id, UpdateBlogRequest request);

    /**
     * Soft delete blog
     */
    void deleteBlog(Long id, Long deletedBy);

    /**
     * Restore deleted blog
     */
    Blog restoreBlog(Long id);

    /**
     * Get all deleted blogs with pagination
     */
    Page<Blog> getDeletedBlogs(Pageable pageable);

    /**
     * Search deleted blogs
     */
    Page<Blog> searchDeletedBlogs(String search, Pageable pageable);

    /**
     * Get comments count for a blog
     */
    long getCommentsCount(Long blogId);
}
