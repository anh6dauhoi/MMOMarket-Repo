package com.mmo.controller;

import com.mmo.entity.Blog;
import com.mmo.entity.User;
import com.mmo.service.BlogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Controller
public class BlogController {

    @Autowired
    private BlogService blogService;

    // list with optional search q and pagination (page starts at 0)
    @GetMapping("/blog")
    public String blogList(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                           @RequestParam(value = "size", required = false, defaultValue = "6") int size,
                           Model model,
                           HttpSession session) {

        Page<Blog> posts = blogService.listPosts(q, page, size);
        model.addAttribute("postsPage", posts);
        model.addAttribute("posts", posts.getContent());
        model.addAttribute("currentPage", posts.getNumber());
        model.addAttribute("totalPages", posts.getTotalPages());
        model.addAttribute("query", q == null ? "" : q.trim());

        Long currentUserId = resolveCurrentUserId(session);
        java.util.Set<Long> likedBlogIds = blogService.getLikedBlogIdsForList(currentUserId, session, posts.getContent());
        model.addAttribute("likedBlogIds", likedBlogIds);

        // NEW: aggregated counts for list (avoid writing Blogs on like/view)
        java.util.Map<String, java.util.Map<Long, Long>> agg = blogService.getAggregatedCountsForBlogs(posts.getContent());
        model.addAttribute("likesMap", agg.get("likesMap"));
        model.addAttribute("viewsMap", agg.get("viewsMap"));
        model.addAttribute("commentsMap", agg.get("commentsMap")); // added for list comment count

        return "customer/blog";
    }

    // detail by identifier: numeric id or a title-based identifier (slug-like)
    @GetMapping("/blog/{identifier}")
    public String blogDetail(@PathVariable("identifier") String identifier, Model model, HttpSession session) {
        Optional<Blog> found = blogService.findByIdentifier(identifier);
        if (!found.isPresent()) return "redirect:/blog";
        Blog post = found.get();

        String sessionId = session.getId();
        Long currentUserId = resolveCurrentUserId(session);

        // delegate to service with currentUserId and session so views/likes persist per-account
        Map<String, Object> vm = blogService.prepareBlogView(post, sessionId, currentUserId, session);

        model.addAttribute("post", post);
        model.addAllAttributes(vm); // includes "comments"

        return "customer/blog-detail";
    }

    // toggle like for a comment (session-based). Returns JSON {count, liked}
    @PostMapping("/blog/comment/{id}/like")
    @ResponseBody
    public Map<String, Object> toggleCommentLike(@PathVariable("id") Long commentId, HttpSession session) {
        Long currentUserId = resolveCurrentUserId(session);
        return blogService.toggleCommentLike(commentId, currentUserId, session);
    }

    // toggle like for a blog (session-based) — UPDATED to use in-memory counters only (no DB writes)
    @PostMapping("/blog/{id}/like")
    @ResponseBody
    public Map<String, Object> toggleBlogLike(@PathVariable("id") Long blogId, HttpSession session) {
        Long currentUserId = resolveCurrentUserId(session);
        return blogService.toggleBlogLike(blogId, currentUserId, session);
    }

    // helper: try multiple session attribute keys / types to obtain current user id
    private Long resolveCurrentUserId(HttpSession session) {
        if (session == null) return null;
        String[] keys = new String[]{"currentUserId", "userId", "currentUser", "user", "loggedUser"};
        for (String k : keys) {
            Object v = session.getAttribute(k);
            if (v == null) continue;
            if (v instanceof Long) return (Long) v;
            if (v instanceof Integer) return ((Integer) v).longValue();
            if (v instanceof User) return ((User) v).getId();
            // some frameworks store a Map/DTO - try common patterns
            try {
                // reflectively try "getId" if present (safe fallback)
                java.lang.reflect.Method m = v.getClass().getMethod("getId");
                Object id = m.invoke(v);
                if (id instanceof Long) return (Long) id;
                if (id instanceof Integer) return ((Integer) id).longValue();
            } catch (Exception ignore) { /* not reflective */ }
        }
        return null;
    }

    // post a comment on a blog. Requires currentUserId in session (Long/Integer/User).
    @PostMapping("/blog/{id}/comment")
    @ResponseBody
    public ResponseEntity<?> addComment(@PathVariable("id") Long blogId,
                                        @RequestParam("content") String content,
                                        @RequestParam(value = "parentId", required = false) Long parentId,
                                        HttpSession session) {

        Long userId = resolveCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "login_required"));
        }

        Map<String, Object> result = blogService.addComment(blogId, content, parentId, userId);
        if (result.containsKey("error")) {
            String err = (String) result.get("error");
            if ("blog_not_found".equals(err)) return ResponseEntity.status(404).body(Map.of("error", err));
            if ("user_not_found".equals(err)) return ResponseEntity.status(401).body(Map.of("error", err));
            return ResponseEntity.badRequest().body(Map.of("error", err));
        }
        return ResponseEntity.ok(result);
    }

    // NEW: GET /blog/new - show new blog form (optional, since popup handles it)
    @GetMapping("/blog/new")
    public String newBlogForm(Model model, HttpSession session) {
        Long currentUserId = resolveCurrentUserId(session);
        if (currentUserId == null) {
            return "redirect:/login"; // or handle unauthorized
        }
        return "customer/blog-new"; // if you want a separate page, but popup is in blog.html
    }

    // NEW: multipart create (supports image upload only; no URL)
    @PostMapping(path = "/blog", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> createBlogMultipart(@RequestParam("title") String title,
                                                 @RequestParam("content") String content,
                                                 @RequestParam(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
                                                 HttpSession session) {
        Long currentUserId = resolveCurrentUserId(session);
        if (currentUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "login_required"));
        }
        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title_and_content_required"));
        }
        // call overload that only accepts file (no URL)
        Map<String, Object> result = blogService.createBlog(title.trim(), content.trim(), imageFile, currentUserId);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // NEW: POST /blog - create new blog
    @PostMapping(path = "/blog", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> createBlog(@RequestParam("title") String title,
                                        @RequestParam("content") String content,
                                        @RequestParam(value = "image", required = false) String image,
                                        HttpSession session) {
        Long currentUserId = resolveCurrentUserId(session);
        if (currentUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "login_required"));
        }
        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title_and_content_required"));
        }
        Map<String, Object> result = blogService.createBlog(title.trim(), content.trim(), image, currentUserId);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // NEW: serve uploaded images safely from the upload directory
    @GetMapping("/blog/image/{filename:.+}")
    public ResponseEntity<?> serveBlogImage(@PathVariable("filename") String filename) {
        try {
            // sanitize filename (prevent path traversal)
            String safe = filename.replace("\\", "/").replaceAll("[^a-zA-Z0-9._-]", "");
            java.nio.file.Path base = java.nio.file.Paths.get(blogService.blogUploadBaseDir());
            java.nio.file.Path path = base.resolve(safe).normalize();
            if (!path.startsWith(base) || !java.nio.file.Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            String ct = java.nio.file.Files.probeContentType(path);
            if (ct == null) {
                String lower = safe.toLowerCase();
                if (lower.endsWith(".png")) ct = MediaType.IMAGE_PNG_VALUE;
                else if (lower.endsWith(".gif")) ct = MediaType.IMAGE_GIF_VALUE;
                else ct = MediaType.IMAGE_JPEG_VALUE;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(ct)).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("cannot_read_image");
        }
    }

    // API for infinite scroll (returns JSON-safe DTOs to ensure full content)
    @GetMapping("/blog/infinite")
    @ResponseBody
    public org.springframework.data.domain.Page<java.util.Map<String, Object>> getBlogsForInfiniteScroll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "6") int size) {

        org.springframework.data.domain.Page<com.mmo.entity.Blog> pageOfBlogs = blogService.listPosts(null, page, size);

        // aggregated counts for the whole page (likes/views/comments)
        java.util.Map<String, java.util.Map<Long, Long>> agg = blogService.getAggregatedCountsForBlogs(pageOfBlogs.getContent());
        java.util.Map<Long, Long> likesMap = agg.getOrDefault("likesMap", java.util.Collections.emptyMap());
        java.util.Map<Long, Long> viewsMap = agg.getOrDefault("viewsMap", java.util.Collections.emptyMap());
        java.util.Map<Long, Long> commentsMap = agg.getOrDefault("commentsMap", java.util.Collections.emptyMap());

        java.util.List<java.util.Map<String, Object>> content = pageOfBlogs.getContent().stream().map(b -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            Long id = b.getId();
            m.put("id", id);
            m.put("title", b.getTitle());
            // read full content directly from DB to avoid Lob/Clob serialization issues
            m.put("content", blogService.getContentById(id));
            m.put("image", b.getImage());
            m.put("likes", likesMap.getOrDefault(id, b.getLikes() == null ? 0L : b.getLikes()));
            m.put("views", viewsMap.getOrDefault(id, b.getViews() == null ? 0L : b.getViews()));
            m.put("comments", commentsMap.getOrDefault(id, 0L));
            m.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().getTime() : null);
            return m;
        }).collect(java.util.stream.Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(
                content,
                pageOfBlogs.getPageable(),
                pageOfBlogs.getTotalElements()
        );
    }

    // Get blogs created by the current user (supports optional search q and pagination)
    @GetMapping("/blog/my")
    public String myBlogs(@RequestParam(value = "q", required = false) String q,
                          @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                          @RequestParam(value = "size", required = false, defaultValue = "6") int size,
                          Model model, HttpSession session) {
        Long currentUserId = resolveCurrentUserId(session);
        if (currentUserId == null) {
            return "redirect:/login"; // Redirect to login if not authenticated
        }

        // Use the new paged service method so the UI can paginate/search "My Posts"
        Page<Blog> postsPage = blogService.listPostsByUser(currentUserId, q, page, size);
        model.addAttribute("postsPage", postsPage);
        model.addAttribute("posts", postsPage.getContent());
        model.addAttribute("currentPage", postsPage.getNumber());
        model.addAttribute("totalPages", postsPage.getTotalPages());
        model.addAttribute("query", q == null ? "" : q.trim());

        java.util.Set<Long> likedBlogIds = blogService.getLikedBlogIdsForList(currentUserId, session, postsPage.getContent());
        model.addAttribute("likedBlogIds", likedBlogIds);

        java.util.Map<String, java.util.Map<Long, Long>> agg = blogService.getAggregatedCountsForBlogs(postsPage.getContent());
        model.addAttribute("likesMap", agg.get("likesMap"));
        model.addAttribute("viewsMap", agg.get("viewsMap"));
        model.addAttribute("commentsMap", agg.get("commentsMap"));

        return "customer/blog";
    }

}
