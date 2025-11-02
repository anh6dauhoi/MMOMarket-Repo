package com.mmo.repository;

<<<<<<< HEAD
import java.util.Optional;

=======
import com.mmo.entity.Blog;
>>>>>>> main
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
<<<<<<< HEAD
import org.springframework.stereotype.Repository;
=======

import java.util.Optional;
>>>>>>> main

import com.mmo.entity.Blog;

@Repository
public interface BlogRepository extends JpaRepository<Blog, Long> {
<<<<<<< HEAD

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
=======
    // Search (newest)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY b.created_at DESC, b.id DESC",
        countQuery =
                "SELECT COUNT(*) FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> searchByTitleOrContent(@Param("term") String term, Pageable pageable);

    // Search (oldest)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY b.created_at ASC, b.id ASC",
        countQuery =
                "SELECT COUNT(*) FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> searchByTitleOrContentOrderByCreatedAsc(@Param("term") String term, Pageable pageable);

    // Search (most liked)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY (COALESCE(b.likes,0) + (SELECT COUNT(1) FROM BlogLikes bl WHERE bl.blog_id = b.id)) DESC, b.created_at DESC, b.id DESC",
        countQuery =
                "SELECT COUNT(*) FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> searchByTitleOrContentOrderByLikes(@Param("term") String term, Pageable pageable);

    // Search (most viewed)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY (COALESCE(b.views,0) + (SELECT COUNT(1) FROM BlogViews bv WHERE bv.blog_id = b.id)) DESC, b.created_at DESC, b.id DESC",
        countQuery =
                "SELECT COUNT(*) FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> searchByTitleOrContentOrderByViews(@Param("term") String term, Pageable pageable);

    // Search (most commented)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY (SELECT COUNT(1) FROM BlogComments bc WHERE bc.blog_id = b.id AND COALESCE(bc.isDelete,0)=0) DESC, b.created_at DESC, b.id DESC",
        countQuery =
                "SELECT COUNT(*) FROM Blogs b " +
                "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                "AND COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> searchByTitleOrContentOrderByComments(@Param("term") String term, Pageable pageable);

    // List (newest)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0 ORDER BY b.created_at DESC, b.id DESC",
        countQuery = "SELECT COUNT(*) FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> findAllActiveNative(Pageable pageable);

    // List (oldest)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0 ORDER BY b.created_at ASC, b.id ASC",
        countQuery = "SELECT COUNT(*) FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> findAllActiveOrderByCreatedAsc(Pageable pageable);

    // List (most liked)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY (COALESCE(b.likes,0) + (SELECT COUNT(1) FROM BlogLikes bl WHERE bl.blog_id = b.id)) DESC, b.created_at DESC, b.id DESC",
        countQuery = "SELECT COUNT(*) FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> findAllActiveOrderByLikes(Pageable pageable);

    // List (most viewed)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY (COALESCE(b.views,0) + (SELECT COUNT(1) FROM BlogViews bv WHERE bv.blog_id = b.id)) DESC, b.created_at DESC, b.id DESC",
        countQuery = "SELECT COUNT(*) FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> findAllActiveOrderByViews(Pageable pageable);

    // List (most commented)
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0 " +
                "ORDER BY (SELECT COUNT(1) FROM BlogComments bc WHERE bc.blog_id = b.id AND COALESCE(bc.isDelete,0)=0) DESC, b.created_at DESC, b.id DESC",
        countQuery = "SELECT COUNT(*) FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Page<Blog> findAllActiveOrderByComments(Pageable pageable);

    // By id active
    @Query(
        value = "SELECT b.id,b.title,b.content,b.image,b.author_id,b.views,b.likes,b.created_at,b.updated_at,b.created_by,b.deleted_by,COALESCE(b.isDelete,0) AS isDelete " +
                "FROM Blogs b WHERE b.id = :id AND COALESCE(b.isDelete,0) = 0",
        nativeQuery = true
    )
    Optional<Blog> findActiveByIdNative(@Param("id") Long id);
>>>>>>> main
}
