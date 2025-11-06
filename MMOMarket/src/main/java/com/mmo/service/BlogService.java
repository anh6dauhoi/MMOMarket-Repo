package com.mmo.service;

import com.mmo.entity.Blog;
import com.mmo.entity.BlogComment;
import com.mmo.entity.User;
import com.mmo.repository.BlogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BlogService {

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // Create helper tables if they don't exist (no new Java files; DB tables are created at runtime)
    @PostConstruct
    public void ensureHelperTables() {
        try {
            // BlogLikes table: blog_id, user_id unique
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS BlogLikes (" +
                            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "blog_id BIGINT NOT NULL, " +
                            "user_id BIGINT NOT NULL, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "UNIQUE KEY uk_blog_user (blog_id, user_id)" +
                            ")"
            );
        } catch (DataAccessException ignored) { /* ignore DDL errors */ }

        try {
            // CommentLikes table: comment_id, user_id unique
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS CommentLikes (" +
                            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "comment_id BIGINT NOT NULL, " +
                            "user_id BIGINT NOT NULL, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "UNIQUE KEY uk_comment_user (comment_id, user_id)" +
                            ")"
            );
        } catch (DataAccessException ignored) { /* ignore DDL errors */ }

        try {
            // BlogViews table: blog_id, user_id unique (first view per account)
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS BlogViews (" +
                            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                            "blog_id BIGINT NOT NULL, " +
                            "user_id BIGINT NOT NULL, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "UNIQUE KEY uk_blog_view_user (blog_id, user_id)" +
                            ")"
            );
        } catch (DataAccessException ignored) { /* ignore DDL errors */ }
    }

    // helper safe DB methods to avoid throwing on empty results / duplicate inserts / other DB oddities
    private int safeCount(String sql, Object... args) {
        try {
            Integer r = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return r == null ? 0 : r;
        } catch (EmptyResultDataAccessException e) {
            return 0;
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private long safeLong(String sql, Object... args) {
        try {
            Long r = jdbcTemplate.queryForObject(sql, Long.class, args);
            return r == null ? 0L : r;
        } catch (EmptyResultDataAccessException e) {
            return 0L;
        } catch (DataAccessException e) {
            return 0L;
        }
    }

    private void safeUpdateIgnoreUnique(String sql, Object... args) {
        try {
            jdbcTemplate.update(sql, args);
        } catch (DataAccessException e) {
            // ignore duplicate-key / constraint violations and other transient DB errors to keep UX smooth
        }
    }

    // List posts with search + sort (SQL handles ordering)
    public Page<Blog> listPosts(String q, String sort, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        String s = (sort == null || sort.isBlank()) ? "newest" : sort.trim().toLowerCase();
        boolean hasQ = q != null && !q.trim().isEmpty();
        String term = hasQ ? q.trim() : null;

        if (!hasQ) {
            switch (s) {
                case "oldest":
                    return blogRepository.findAllActiveOrderByCreatedAsc(pageable);
                case "most-liked":
                    return blogRepository.findAllActiveOrderByLikes(pageable);
                case "most-viewed":
                    return blogRepository.findAllActiveOrderByViews(pageable);
                case "most-commented":
                    return blogRepository.findAllActiveOrderByComments(pageable);
                case "newest":
                default:
                    return blogRepository.findAllActiveNative(pageable);
            }
        } else {
            switch (s) {
                case "oldest":
                    return blogRepository.searchByTitleOrContentOrderByCreatedAsc(term, pageable);
                case "most-liked":
                    return blogRepository.searchByTitleOrContentOrderByLikes(term, pageable);
                case "most-viewed":
                    return blogRepository.searchByTitleOrContentOrderByViews(term, pageable);
                case "most-commented":
                    return blogRepository.searchByTitleOrContentOrderByComments(term, pageable);
                case "newest":
                default:
                    return blogRepository.searchByTitleOrContent(term, pageable);
            }
        }
    }

    // Resolve identifier: reuse ordered native search; no additional re-sort needed
    public Optional<Blog> findByIdentifier(String identifier) {
        Optional<Blog> found = Optional.empty();
        try {
            Long id = Long.parseLong(identifier);
            found = blogRepository.findActiveByIdNative(id);
        } catch (NumberFormatException ignored) { }

        if (found.isPresent()) return found;

        String decoded = URLDecoder.decode(identifier, StandardCharsets.UTF_8);
        String candidateTitle = decoded.replace('-', ' ').trim();
        if (!candidateTitle.isEmpty()) {
            Page<Blog> page = blogRepository.searchByTitleOrContent(candidateTitle, PageRequest.of(0, 1));
            if (!page.getContent().isEmpty()) return Optional.of(page.getContent().get(0));
        }
        return Optional.empty();
    }

    // Prepare view model data: enforce single view per-account or once per session for guests; compute like flags.
    @Transactional
    public Map<String, Object> prepareBlogView(Blog post, String sessionId, Long currentUserId, HttpSession session) {
        Map<String, Object> out = new HashMap<>();
        Long blogId = post.getId();

        // --- Views: only count first view per-account (persisted) or once per-session for guests
        boolean justIncremented = false;
        if (currentUserId != null) {
            int exists = safeCount("SELECT COUNT(1) FROM BlogViews WHERE blog_id = ? AND user_id = ?", blogId, currentUserId);
            if (exists == 0) {
                // insert a unique view record; no UPDATE on Blogs
                safeUpdateIgnoreUnique("INSERT INTO BlogViews (blog_id, user_id) VALUES (?, ?)", blogId, currentUserId);
                justIncremented = true;
            }
        } else {
            @SuppressWarnings("unchecked")
            Set<Long> seen = (Set<Long>) session.getAttribute("VIEWED_BLOGS_SESSION_SET");
            if (seen == null) {
                seen = Collections.newSetFromMap(new ConcurrentHashMap<>());
                session.setAttribute("VIEWED_BLOGS_SESSION_SET", seen);
            }
            if (!seen.contains(blogId)) {
                seen.add(blogId);
                // guests do not mutate DB; only per-session view
                justIncremented = true;
            }
        }

        // Base values from Blogs table (seeded) + derived counts from helper tables
        long baseViews = post.getViews() == null ? 0L : post.getViews();
        long extraViews = safeLong("SELECT COUNT(1) FROM BlogViews WHERE blog_id = ?", blogId);
        long displayViews = baseViews + extraViews;

        long baseLikes = post.getLikes() == null ? 0L : post.getLikes();
        long extraLikes = safeLong("SELECT COUNT(1) FROM BlogLikes WHERE blog_id = ?", blogId);
        long displayLikes = baseLikes + extraLikes;

        // blog liked state
        boolean isBlogLiked = false;
        if (currentUserId != null) {
            isBlogLiked = safeCount("SELECT COUNT(1) FROM BlogLikes WHERE blog_id = ? AND user_id = ?", blogId, currentUserId) > 0;
        } else {
            @SuppressWarnings("unchecked")
            Set<Long> guestLiked = (Set<Long>) session.getAttribute("GUEST_LIKED_BLOGS");
            isBlogLiked = guestLiked != null && guestLiked.contains(blogId);
        }

        // Load comments safely via native query to avoid null->primitive mapping on isDelete
        List<BlogComment> comments = new ArrayList<>();
        try {
            comments = entityManager
                    .createNativeQuery(
                            "SELECT " +
                                    " c.id, c.blog_id, c.user_id, c.content, c.parent_comment_id, " +
                                    " c.created_at, c.updated_at, " +
                                    " c.created_by, c.deleted_by, " +
                                    " COALESCE(c.isDelete,0) AS isDelete " +
                                    "FROM BlogComments c " +
                                    "WHERE c.blog_id = ? AND COALESCE(c.isDelete,0) = 0 " +
                                    "ORDER BY c.created_at ASC, c.id ASC",
                            BlogComment.class
                    )
                    .setParameter(1, blogId)
                    .getResultList();
        } catch (Exception ignored) { /* keep empty if mapping fails */ }

        // comment likes and liked set computed from the loaded list
        Map<Long, Long> commentLikes = new HashMap<>();
        Set<Long> likedComments = new HashSet<>();
        for (BlogComment c : comments) {
            long cnt = safeLong("SELECT COUNT(1) FROM CommentLikes WHERE comment_id = ?", c.getId());
            commentLikes.put(c.getId(), cnt);
            boolean liked = false;
            if (currentUserId != null) {
                liked = safeCount("SELECT COUNT(1) FROM CommentLikes WHERE comment_id = ? AND user_id = ?", c.getId(), currentUserId) > 0;
            } else {
                @SuppressWarnings("unchecked")
                Set<Long> guestCl = (Set<Long>) session.getAttribute("GUEST_LIKED_COMMENTS");
                liked = guestCl != null && guestCl.contains(c.getId());
            }
            if (liked) likedComments.add(c.getId());
        }

        out.put("displayViews", displayViews);
        out.put("displayLikes", displayLikes);
        out.put("isBlogLiked", isBlogLiked);
        out.put("commentLikes", commentLikes);
        out.put("likedComments", likedComments);
        out.put("comments", comments); // provide safe comment list to the view
        out.put("justIncrementedView", justIncremented);
        return out;
    }

    // Toggle blog like: persisted for logged-in users (prevent re-like after logout); guests are not allowed to like
    @Transactional
    public Map<String, Object> toggleBlogLike(Long blogId, Long currentUserId, HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        if (currentUserId == null) {
            resp.put("error", "login_required");
            return resp;
        }
        int exists = safeCount("SELECT COUNT(1) FROM BlogLikes WHERE blog_id = ? AND user_id = ?", blogId, currentUserId);
        if (exists > 0) {
            // unlike
            try { jdbcTemplate.update("DELETE FROM BlogLikes WHERE blog_id = ? AND user_id = ?", blogId, currentUserId); } catch (DataAccessException ignored) {}
        } else {
            // like (ignore duplicate key if happens)
            safeUpdateIgnoreUnique("INSERT INTO BlogLikes (blog_id, user_id) VALUES (?, ?)", blogId, currentUserId);
        }
        // aggregated count: base seed + derived likes
        long baseLikes = safeLong("SELECT COALESCE(likes,0) FROM Blogs WHERE id = ?", blogId);
        long extraLikes = safeLong("SELECT COUNT(1) FROM BlogLikes WHERE blog_id = ?", blogId);
        long likes = baseLikes + extraLikes;
        boolean likedNow = safeCount("SELECT COUNT(1) FROM BlogLikes WHERE blog_id = ? AND user_id = ?", blogId, currentUserId) > 0;
        resp.put("count", likes);
        resp.put("liked", likedNow);
        return resp;
    }

    // Toggle comment like: persisted for logged-in users; guest toggles session set (no DB change)
    @Transactional
    public Map<String, Object> toggleCommentLike(Long commentId, Long currentUserId, HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        if (currentUserId != null) {
            int exists = safeCount("SELECT COUNT(1) FROM CommentLikes WHERE comment_id = ? AND user_id = ?", commentId, currentUserId);
            if (exists > 0) {
                try { jdbcTemplate.update("DELETE FROM CommentLikes WHERE comment_id = ? AND user_id = ?", commentId, currentUserId); } catch (DataAccessException ignored) {}
            } else {
                safeUpdateIgnoreUnique("INSERT INTO CommentLikes (comment_id, user_id) VALUES (?, ?)", commentId, currentUserId);
            }
            long count = safeLong("SELECT COUNT(1) FROM CommentLikes WHERE comment_id = ?", commentId);
            boolean likedNow = safeCount("SELECT COUNT(1) FROM CommentLikes WHERE comment_id = ? AND user_id = ?", commentId, currentUserId) > 0;
            resp.put("count", count);
            resp.put("liked", likedNow);
            return resp;
        } else {
            @SuppressWarnings("unchecked")
            Set<Long> guestCl = (Set<Long>) session.getAttribute("GUEST_LIKED_COMMENTS");
            if (guestCl == null) {
                guestCl = Collections.newSetFromMap(new ConcurrentHashMap<>());
                session.setAttribute("GUEST_LIKED_COMMENTS", guestCl);
            }
            boolean nowLiked = !guestCl.contains(commentId);
            if (nowLiked) guestCl.add(commentId); else guestCl.remove(commentId);
            long count = safeLong("SELECT COUNT(1) FROM CommentLikes WHERE comment_id = ?", commentId);
            resp.put("count", count);
            resp.put("liked", nowLiked);
            return resp;
        }
    }

    // NEW: aggregated counts for a page of blogs (likes/views)
    public Map<String, Map<Long, Long>> getAggregatedCountsForBlogs(List<Blog> blogs) {
        Map<String, Map<Long, Long>> out = new HashMap<>();
        Map<Long, Long> likesMap = new HashMap<>();
        Map<Long, Long> viewsMap = new HashMap<>();
        Map<Long, Long> commentsMap = new HashMap<>();
        if (blogs != null) {
            for (Blog b : blogs) {
                Long id = b.getId();
                long baseLikes = b.getLikes() == null ? 0L : b.getLikes();
                long extraLikes = safeLong("SELECT COUNT(1) FROM BlogLikes WHERE blog_id = ?", id);
                likesMap.put(id, baseLikes + extraLikes);

                long baseViews = b.getViews() == null ? 0L : b.getViews();
                long extraViews = safeLong("SELECT COUNT(1) FROM BlogViews WHERE blog_id = ?", id);
                viewsMap.put(id, baseViews + extraViews);

                long commentsCnt = safeLong("SELECT COUNT(1) FROM BlogComments WHERE blog_id = ? AND COALESCE(isDelete,0) = 0", id);
                commentsMap.put(id, commentsCnt);
            }
        }
        out.put("likesMap", likesMap);
        out.put("viewsMap", viewsMap);
        out.put("commentsMap", commentsMap);
        return out;
    }

    // Add comment: avoid hydrating Blog (prevents NULL -> primitive boolean mapping crash)
    @Transactional
    public Map<String, Object> addComment(Long blogId, String content, Long parentId, Long currentUserId) {
        Map<String, Object> resp = new HashMap<>();
        if (content == null || content.trim().isEmpty()) {
            resp.put("error", "empty_content");
            return resp;
        }

        // Only verify existence using a safe COUNT; do NOT load entity (would trigger null->boolean issue)
        long exists = safeLong("SELECT COUNT(1) FROM Blogs WHERE id = ? AND COALESCE(isDelete,0) = 0", blogId);
        if (exists == 0L) {
            resp.put("error", "blog_not_found");
            return resp;
        }

        User user = entityManager.find(User.class, currentUserId);
        if (user == null) {
            resp.put("error", "user_not_found");
            return resp;
        }

        // Set FK via a reference to avoid loading full Blog row
        Blog blogRef = entityManager.getReference(Blog.class, blogId);

        BlogComment comment = new BlogComment();
        comment.setBlog(blogRef);
        comment.setUser(user);
        comment.setContent(content.trim());
        comment.setCreatedAt(new Date());

        if (parentId != null) {
            // Optional: ensure parent exists (lightweight check)
            long parentExists = safeLong("SELECT COUNT(1) FROM BlogComments WHERE id = ? AND COALESCE(isDelete,0) = 0", parentId);
            if (parentExists > 0) {
                BlogComment parent = entityManager.getReference(BlogComment.class, parentId);
                comment.setParentComment(parent);
            }
        }

        entityManager.persist(comment);
        entityManager.flush();

        Map<String, Object> data = new HashMap<>();
        data.put("id", comment.getId());
        data.put("author", user.getFullName());
        data.put("createdAt", comment.getCreatedAt().getTime());
        data.put("content", comment.getContent());

        resp.put("ok", true);
        resp.put("comment", data);
        return resp;
    }

    // NEW: compute liked blog ids for list rendering (logged-in uses BlogLikes; guest uses session set)
    public java.util.Set<Long> getLikedBlogIdsForList(Long currentUserId, jakarta.servlet.http.HttpSession session, java.util.List<com.mmo.entity.Blog> blogs) {
        java.util.Set<Long> result = new java.util.HashSet<>();
        if (blogs == null || blogs.isEmpty()) return result;

        java.util.List<Long> ids = blogs.stream()
                .map(com.mmo.entity.Blog::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (ids.isEmpty()) return result;

        if (currentUserId != null) {
            // Build IN clause dynamically
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            java.util.List<Object> params = new java.util.ArrayList<>();
            params.add(currentUserId);
            params.addAll(ids);
            try {
                // Use queryForList to avoid ambiguous query(...) overload
                java.util.List<Long> liked = jdbcTemplate.queryForList(
                        "SELECT blog_id FROM BlogLikes WHERE user_id = ? AND blog_id IN (" + placeholders + ")",
                        params.toArray(),
                        Long.class
                );
                if (liked != null) result.addAll(liked);
            } catch (org.springframework.dao.DataAccessException ignored) { }
        } else {
            @SuppressWarnings("unchecked")
            java.util.Set<Long> guestLiked = (java.util.Set<Long>) session.getAttribute("GUEST_LIKED_BLOGS");
            if (guestLiked != null) {
                for (Long id : ids) {
                    if (guestLiked.contains(id)) result.add(id);
                }
            }
        }
        return result;
    }


    // NEW: safely read blog content as a plain String (avoids Clob/Lob serialization issues)
    public String getContentById(Long blogId) {
        if (blogId == null) return "";
        try {
            String content = jdbcTemplate.queryForObject(
                    "SELECT content FROM Blogs WHERE id = ?",
                    new Object[]{blogId},
                    String.class
            );
            return content == null ? "" : content;
        } catch (org.springframework.dao.DataAccessException ex) {
            return "";
        }
    }

    // Admin methods for blog management
    @Transactional
    public Blog createBlog(com.mmo.dto.CreateBlogRequest request, Long adminId) {
        Blog blog = new Blog();
        blog.setTitle(request.getTitle());
        blog.setContent(request.getContent());
        blog.setImage(request.getImage());
        blog.setCreatedBy(adminId);
        blog.setStatus(true); // Active by default
        blog.setDelete(false);
        blog.setCreatedAt(new java.util.Date());
        return blogRepository.save(blog);
    }

    public Blog getBlogById(Long id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found with id: " + id));
    }

    public long getCommentsCount(Long blogId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM BlogComments WHERE blog_id = ? AND COALESCE(isDelete,0) = 0",
                    new Object[]{blogId},
                    Long.class
            );
            return count == null ? 0L : count;
        } catch (org.springframework.dao.DataAccessException ex) {
            return 0L;
        }
    }

    @Transactional
    public Blog updateBlog(Long id, com.mmo.dto.UpdateBlogRequest request) {
        Blog blog = getBlogById(id);
        if (request.getTitle() != null) {
            blog.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            blog.setContent(request.getContent());
        }
        if (request.getImage() != null) {
            blog.setImage(request.getImage());
        }
        blog.setUpdatedAt(new java.util.Date());
        return blogRepository.save(blog);
    }

    @Transactional
    public Blog toggleBlogStatus(Long id) {
        Blog blog = getBlogById(id);
        blog.setStatus(!blog.isStatus());
        return blogRepository.save(blog);
    }
}

