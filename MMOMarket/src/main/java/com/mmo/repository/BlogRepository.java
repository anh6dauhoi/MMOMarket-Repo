package com.mmo.repository;

import com.mmo.entity.Blog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BlogRepository extends JpaRepository<Blog, Long> {
    // Safe native search: include all mapped columns and coalesce isDelete. Newest first.
    @Query(
            value =
                    "SELECT " +
                            " b.id, b.title, b.content, b.image, " +
                            " b.author_id, b.views, b.likes, " +
                            " b.created_at, b.updated_at, " +
                            " b.created_by, b.deleted_by, " +
                            " COALESCE(b.isDelete,0) AS isDelete " +
                            "FROM Blogs b " +
                            "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') " +
                            "   OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                            "AND COALESCE(b.isDelete,0) = 0 " +
                            "ORDER BY b.created_at DESC, b.id DESC",
            countQuery =
                    "SELECT COUNT(*) FROM Blogs b " +
                            "WHERE (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') " +
                            "   OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                            "AND COALESCE(b.isDelete,0) = 0",
            nativeQuery = true
    )
    Page<Blog> searchByTitleOrContent(@Param("term") String term, Pageable pageable);

    // Safe list: exclude deleted, include all mapped columns. Newest first.
    @Query(
            value =
                    "SELECT " +
                            " b.id, b.title, b.content, b.image, " +
                            " b.author_id, b.views, b.likes, " +
                            " b.created_at, b.updated_at, " +
                            " b.created_by, b.deleted_by, " +
                            " COALESCE(b.isDelete,0) AS isDelete " +
                            "FROM Blogs b " +
                            "WHERE COALESCE(b.isDelete,0) = 0 " +
                            "ORDER BY b.created_at DESC, b.id DESC",
            countQuery = "SELECT COUNT(*) FROM Blogs b WHERE COALESCE(b.isDelete,0) = 0",
            nativeQuery = true
    )
    Page<Blog> findAllActiveNative(Pageable pageable);

    // optional: order by createdAt desc (fallback to repository .findAll(Pageable) with sort)
    Page<Blog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // find first blog by exact title (case-insensitive) — used as a name/slug fallback
    Optional<Blog> findFirstByTitleIgnoreCase(String title);

    // Replaced JPQL updates with native updates to avoid managed-entity full-updates that previously caused DB trigger errors
    @Modifying
    @Transactional
    @Query(value = "UPDATE Blogs SET views = COALESCE(views,0) + 1 WHERE id = :id", nativeQuery = true)
    void incrementViews(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE Blogs SET likes = COALESCE(likes,0) + 1 WHERE id = :id", nativeQuery = true)
    void incrementLikes(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE Blogs SET likes = CASE WHEN likes IS NULL OR likes <= 0 THEN 0 ELSE likes - 1 END WHERE id = :id", nativeQuery = true)
    void decrementLikes(@Param("id") Long id);

    // Native by id: include all mapped columns; skip deleted; newest-first is irrelevant here
    @Query(
            value =
                    "SELECT " +
                            " b.id, b.title, b.content, b.image, " +
                            " b.author_id, b.views, b.likes, " +
                            " b.created_at, b.updated_at, " +
                            " b.created_by, b.deleted_by, " +
                            " COALESCE(b.isDelete,0) AS isDelete " +
                            "FROM Blogs b " +
                            "WHERE b.id = :id AND COALESCE(b.isDelete,0) = 0",
            nativeQuery = true
    )
    Optional<Blog> findActiveByIdNative(@Param("id") Long id);

    // Find all blogs by a specific author
    @Query("SELECT b FROM Blog b WHERE b.author.id = :authorId AND b.isDelete = false ORDER BY b.createdAt DESC")
    java.util.List<Blog> findAllByAuthorId(@Param("authorId") Long authorId);

    // Find all active blogs by author (paged, native) - used for "My Posts"
    @Query(
            value =
                    "SELECT " +
                            " b.id, b.title, b.content, b.image, " +
                            " b.author_id, b.views, b.likes, " +
                            " b.created_at, b.updated_at, " +
                            " b.created_by, b.deleted_by, " +
                            " COALESCE(b.isDelete,0) AS isDelete " +
                            "FROM Blogs b " +
                            "WHERE b.author_id = :authorId AND COALESCE(b.isDelete,0) = 0 " +
                            "ORDER BY b.created_at DESC, b.id DESC",
            countQuery = "SELECT COUNT(*) FROM Blogs b WHERE b.author_id = :authorId AND COALESCE(b.isDelete,0) = 0",
            nativeQuery = true
    )
    Page<Blog> findAllActiveByAuthorNative(@Param("authorId") Long authorId, Pageable pageable);

    // Search active blogs by author + term (paged, native)
    @Query(
            value =
                    "SELECT " +
                            " b.id, b.title, b.content, b.image, " +
                            " b.author_id, b.views, b.likes, " +
                            " b.created_at, b.updated_at, " +
                            " b.created_by, b.deleted_by, " +
                            " COALESCE(b.isDelete,0) AS isDelete " +
                            "FROM Blogs b " +
                            "WHERE b.author_id = :authorId " +
                            "  AND COALESCE(b.isDelete,0) = 0 " +
                            "  AND (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') " +
                            "       OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%')) " +
                            "ORDER BY b.created_at DESC, b.id DESC",
            countQuery =
                    "SELECT COUNT(*) FROM Blogs b " +
                            "WHERE b.author_id = :authorId " +
                            "  AND COALESCE(b.isDelete,0) = 0 " +
                            "  AND (LOWER(b.title) LIKE CONCAT('%', LOWER(:term), '%') " +
                            "       OR LOWER(b.content) LIKE CONCAT('%', LOWER(:term), '%'))",
            nativeQuery = true
    )
    Page<Blog> searchByAuthorAndTerm(@Param("authorId") Long authorId, @Param("term") String term, Pageable pageable);
}
