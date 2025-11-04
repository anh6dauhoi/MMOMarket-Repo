package com.mmo.dto;

import java.util.Date;

import com.mmo.entity.Blog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogResponse {
    private Long id;
    private String title;
    private String content;
    private String image;
    private String authorName;
    private Long authorId;
    private Long views;
    private Long likes;
    private Long commentsCount;
    private Date createdAt;
    private Date updatedAt;
    private String deletedBy;

    public static BlogResponse fromEntity(Blog blog) {
        return fromEntity(blog, 0L);
    }

    public static BlogResponse fromEntity(Blog blog, Long commentsCount) {
        BlogResponse response = new BlogResponse();
        response.setId(blog.getId());
        response.setTitle(blog.getTitle());
        response.setContent(blog.getContent());
        response.setImage(blog.getImage());
        response.setAuthorName(blog.getAuthor() != null ? blog.getAuthor().getFullName() : "Unknown");
        response.setAuthorId(blog.getAuthor() != null ? blog.getAuthor().getId() : null);
        response.setViews(blog.getViews());
        response.setLikes(blog.getLikes());
        response.setCommentsCount(commentsCount);
        response.setCreatedAt(blog.getCreatedAt());
        response.setUpdatedAt(blog.getUpdatedAt());
        response.setDeletedBy(blog.getDeletedByUser() != null ? blog.getDeletedByUser().getEmail() : null);
        return response;
    }
}
