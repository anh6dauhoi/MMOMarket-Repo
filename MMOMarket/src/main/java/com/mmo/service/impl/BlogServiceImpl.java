package com.mmo.service.impl;

import com.mmo.dto.CreateBlogRequest;
import com.mmo.dto.UpdateBlogRequest;
import com.mmo.entity.Blog;
import com.mmo.entity.User;
import com.mmo.repository.BlogRepository;
import com.mmo.repository.UserRepository;
import com.mmo.service.BlogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class BlogServiceImpl implements BlogService {

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Page<Blog> getAllBlogs(Pageable pageable) {
        return blogRepository.findByIsDeleteOrderByCreatedAtDesc(false, pageable);
    }

    @Override
    public Page<Blog> getVisibleBlogs(Pageable pageable) {
        return blogRepository.findByStatusAndIsDeleteOrderByCreatedAtDesc(true, false, pageable);
    }

    @Override
    public Page<Blog> searchBlogs(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return getAllBlogs(pageable);
        }
        return blogRepository.searchBlogsByTitle(search.trim(), false, pageable);
    }

    @Override
    public Page<Blog> searchVisibleBlogs(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return getVisibleBlogs(pageable);
        }
        return blogRepository.searchVisibleBlogsByTitle(search.trim(), true, false, pageable);
    }

    @Override
    public Blog getBlogById(Long id) {
        return blogRepository.findByIdAndIsDelete(id, false)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found with id: " + id));
    }

    @Override
    public Blog getVisibleBlogById(Long id) {
        return blogRepository.findByIdAndStatusAndIsDelete(id, true, false)
                .orElseThrow(() -> new IllegalArgumentException("Visible blog not found with id: " + id));
    }

    @Override
    @Transactional
    public Blog createBlog(CreateBlogRequest request, Long authorId) {
        // Validate input
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Blog title is required");
        }
        if (request.getTitle().length() < 5 || request.getTitle().length() > 255) {
            throw new IllegalArgumentException("Blog title must be between 5 and 255 characters");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Blog content is required");
        }
        if (request.getContent().length() < 10) {
            throw new IllegalArgumentException("Blog content must be at least 10 characters");
        }

        // Get author
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("Author not found"));

        // Create blog
        Blog blog = new Blog();
        blog.setTitle(request.getTitle().trim());
        blog.setContent(request.getContent().trim());
        blog.setImage(request.getImage());
        blog.setAuthor(author);
        blog.setViews(0L);
        blog.setLikes(0L);
        blog.setCreatedAt(new Date());
        blog.setUpdatedAt(new Date());
        blog.setCreatedBy(authorId);
        blog.setDelete(false);

        return blogRepository.save(blog);
    }

    @Override
    @Transactional
    public Blog updateBlog(Long id, UpdateBlogRequest request) {
        Blog blog = getBlogById(id);

        // Validate input
        if (request.getTitle() != null) {
            if (request.getTitle().trim().isEmpty()) {
                throw new IllegalArgumentException("Blog title cannot be empty");
            }
            if (request.getTitle().length() < 5 || request.getTitle().length() > 255) {
                throw new IllegalArgumentException("Blog title must be between 5 and 255 characters");
            }
            blog.setTitle(request.getTitle().trim());
        }

        if (request.getContent() != null) {
            if (request.getContent().trim().isEmpty()) {
                throw new IllegalArgumentException("Blog content cannot be empty");
            }
            if (request.getContent().length() < 10) {
                throw new IllegalArgumentException("Blog content must be at least 10 characters");
            }
            blog.setContent(request.getContent().trim());
        }

        if (request.getImage() != null) {
            blog.setImage(request.getImage());
        }

        blog.setUpdatedAt(new Date());
        return blogRepository.save(blog);
    }

    @Override
    @Transactional
    public Blog toggleBlogStatus(Long id) {
        Blog blog = getBlogById(id);
        blog.setStatus(!blog.isStatus());
        blog.setUpdatedAt(new Date());
        return blogRepository.save(blog);
    }

    @Override
    @Transactional
    public Blog setBlogStatus(Long id, boolean status) {
        Blog blog = getBlogById(id);
        blog.setStatus(status);
        blog.setUpdatedAt(new Date());
        return blogRepository.save(blog);
    }

    @Override
    @Transactional
    public void deleteBlog(Long id, Long deletedBy) {
        Blog blog = getBlogById(id);
        blog.setDelete(true);
        blog.setDeletedBy(deletedBy);
        blogRepository.save(blog);
    }

    @Override
    @Transactional
    public Blog restoreBlog(Long id) {
        Blog blog = blogRepository.findByIdAndIsDelete(id, true)
                .orElseThrow(() -> new IllegalArgumentException("Deleted blog not found with id: " + id));

        blog.setDelete(false);
        blog.setDeletedBy(null);
        return blogRepository.save(blog);
    }

    @Override
    public Page<Blog> getDeletedBlogs(Pageable pageable) {
        return blogRepository.findByIsDeleteOrderByCreatedAtDesc(true, pageable);
    }

    @Override
    public Page<Blog> searchDeletedBlogs(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return getDeletedBlogs(pageable);
        }
        return blogRepository.searchBlogsByTitle(search.trim(), true, pageable);
    }

    @Override
    public long getCommentsCount(Long blogId) {
        return blogRepository.countCommentsByBlogId(blogId);
    }
}
