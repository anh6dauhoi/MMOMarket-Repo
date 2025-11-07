package com.mmo.controller;

import com.mmo.entity.Orders;
import com.mmo.entity.Review;
import com.mmo.entity.User;
import com.mmo.repository.OrdersRepository;
import com.mmo.repository.ReviewRepository;
import com.mmo.repository.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Controller
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private UserRepository userRepository;

    private String resolveEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        String email = authentication.getName();
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauthUser = oauth2Token.getPrincipal();
            String mail = oauthUser.getAttribute("email");
            if (mail != null) email = mail;
        }
        return email;
    }

    // Show review form for specific order (only if not yet reviewed)
    @GetMapping("/account/orders/{orderId}/review")
    public String reviewForm(@PathVariable Long orderId, Authentication authentication, Model model) {
        String email = resolveEmail(authentication);
        if (email == null) return "redirect:/authen/login";
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/authen/login";
        User user = userOpt.get();
        // user must be active (not deleted)
        if (user.isDelete()) {
            return "redirect:/account/orders"; // could show message in future
        }
        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null || !order.getCustomerId().equals(user.getId())) {
            return "redirect:/account/orders";
        }
        if (order.getStatus() != Orders.QueueStatus.COMPLETED) {
            return "redirect:/account/orders";
        }
        // 30-day cutoff strictly from purchase date (createdAt)
        Date baseDate = order.getCreatedAt();
        if (baseDate != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(baseDate.toInstant(), java.time.Instant.now());
            if (days > 30) {
                return "redirect:/account/orders";
            }
        }

        // Check if user has already reviewed THIS specific order
        boolean alreadyReviewed = reviewRepository.existsByUser_IdAndOrder_IdAndIsDeleteFalse(user.getId(), orderId);
        if (alreadyReviewed) {
            // Already reviewed this order, redirect to view
            return "redirect:/account/orders/" + orderId + "/review/view";
        }
        // Prepare model for UI
        Long productId = order.getProductId();
        String displayImage = null;
        try {
            Object p = order.getProduct();
            if (p != null) {
                for (String getter : List.of("getImageUrl", "getImage", "getThumbnail")) {
                    try {
                        var m = p.getClass().getMethod(getter);
                        Object val = m.invoke(p);
                        if (val instanceof String s && !s.isBlank()) { displayImage = s; break; }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        if (displayImage == null) {
            displayImage = "/images/home.jpg"; // fallback
        }
        Double avg = 0.0;
        try { avg = reviewRepository.getAverageRatingByProduct(productId); } catch (Exception ignored) {}
        if (avg == null) avg = 0.0;
        int avgRounded = (int) Math.round(avg);
        List<Review> reviews = reviewRepository.findByProductIdAndIsDeleteFalseOrderByCreatedAtDesc(productId);
        int totalReviews = reviews != null ? reviews.size() : 0;
        int[] buckets = new int[]{0,0,0,0,0,0}; // 0..5
        List<Map<String, Object>> reviewItems = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        if (reviews != null) {
            for (Review r : reviews) {
                int star = (r.getRating() != null) ? Math.max(1, Math.min(5, r.getRating())) : 0;
                buckets[star]++;
                Map<String, Object> it = new HashMap<>();
                String fullName = null;
                try { fullName = r.getUser() != null ? r.getUser().getFullName() : null; } catch (Exception ignored) {}
                if (fullName == null || fullName.isBlank()) fullName = "Khách hàng";
                it.put("name", fullName);
                it.put("rating", star);
                it.put("comment", r.getComment() != null ? r.getComment() : "");
                it.put("date", r.getCreatedAt() != null ? sdf.format(r.getCreatedAt()) : "");
                reviewItems.add(it);
            }
        }
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            int count = buckets[star];
            double percent = totalReviews > 0 ? (count * 100.0 / totalReviews) : 0.0;
            Map<String, Object> d = new HashMap<>();
            d.put("star", star);
            d.put("count", count);
            d.put("percent", Math.round(percent));
            distribution.add(d);
        }
        model.addAttribute("orderId", orderId);
        model.addAttribute("productName", order.getProduct() != null ? order.getProduct().getName() : ("#" + order.getProductId()));
        model.addAttribute("productImage", displayImage);
        model.addAttribute("avgRating", Math.round(avg * 10.0) / 10.0);
        model.addAttribute("avgRounded", avgRounded);
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("distribution", distribution);
        model.addAttribute("reviews", reviewItems);
        model.addAttribute("reviewRequest", new ReviewRequest());
        return "customer/review-form";
    }

    // View my review for this order's product
    @GetMapping("/account/orders/{orderId}/review/view")
    public String viewMyReview(@PathVariable Long orderId, Authentication authentication, Model model) {
        String email = resolveEmail(authentication);
        if (email == null) return "redirect:/authen/login";
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/authen/login";
        User user = userOpt.get();
        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null || !order.getCustomerId().equals(user.getId())) return "redirect:/account/orders";

        // Find review for THIS specific order
        var optMine = reviewRepository.findByUser_IdAndOrder_IdAndIsDeleteFalse(user.getId(), orderId);
        if (optMine.isEmpty()) {
            // No review for this order yet, redirect to create form
            return "redirect:/account/orders/" + orderId + "/review";
        }
        Review mine = optMine.get();

        // Can edit within 7 days
        boolean canEdit = false;
        Date created = mine.getCreatedAt();
        if (created != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(created.toInstant(), java.time.Instant.now());
            canEdit = days <= 7;
        }

        // Prepare common product model (same as reviewForm)
        Long productId = order.getProductId();
        String displayImage = null;
        try {
            Object p = order.getProduct();
            if (p != null) {
                for (String getter : List.of("getImageUrl", "getImage", "getThumbnail")) {
                    try {
                        var m = p.getClass().getMethod(getter);
                        Object val = m.invoke(p);
                        if (val instanceof String s && !s.isBlank()) { displayImage = s; break; }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        if (displayImage == null) displayImage = "/images/home.jpg";

        Double avg = 0.0;
        try { avg = reviewRepository.getAverageRatingByProduct(productId); } catch (Exception ignored) {}
        if (avg == null) avg = 0.0;
        int avgRounded = (int) Math.round(avg);
        List<Review> reviews = reviewRepository.findByProductIdAndIsDeleteFalseOrderByCreatedAtDesc(productId);
        int totalReviews = reviews != null ? reviews.size() : 0;
        int[] buckets = new int[]{0,0,0,0,0,0};
        List<Map<String, Object>> reviewItems = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        if (reviews != null) {
            for (Review r : reviews) {
                int star = (r.getRating() != null) ? Math.max(1, Math.min(5, r.getRating())) : 0;
                buckets[star]++;
                Map<String, Object> it = new HashMap<>();
                String fullName = null;
                try { fullName = r.getUser() != null ? r.getUser().getFullName() : null; } catch (Exception ignored) {}
                if (fullName == null || fullName.isBlank()) fullName = "Khách hàng";
                it.put("name", fullName);
                it.put("rating", star);
                it.put("comment", r.getComment() != null ? r.getComment() : "");
                it.put("date", r.getCreatedAt() != null ? sdf.format(r.getCreatedAt()) : "");
                reviewItems.add(it);
            }
        }
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            int count = buckets[star];
            double percent = totalReviews > 0 ? (count * 100.0 / totalReviews) : 0.0;
            Map<String, Object> d = new HashMap<>();
            d.put("star", star);
            d.put("count", count);
            d.put("percent", Math.round(percent));
            distribution.add(d);
        }

        // Populate model
        model.addAttribute("orderId", orderId);
        model.addAttribute("productName", order.getProduct() != null ? order.getProduct().getName() : ("#" + order.getProductId()));
        model.addAttribute("productImage", displayImage);
        model.addAttribute("avgRating", Math.round(avg * 10.0) / 10.0);
        model.addAttribute("avgRounded", avgRounded);
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("distribution", distribution);
        model.addAttribute("reviews", reviewItems);

        // my review payload
        Map<String,Object> my = new HashMap<>();
        my.put("rating", mine.getRating());
        my.put("comment", mine.getComment());
        my.put("createdAt", mine.getCreatedAt());
        my.put("reviewId", mine.getId());
        model.addAttribute("myReview", my);
        model.addAttribute("canEdit", canEdit);
        model.addAttribute("reviewId", mine.getId());

        // Reuse the unified template
        return "customer/review-form";
    }

    // Submit review
    @PostMapping("/account/orders/{orderId}/review")
    @Transactional
    public String submitReview(@PathVariable Long orderId,
                               @ModelAttribute("reviewRequest") ReviewRequest reviewRequest,
                               BindingResult bindingResult,
                               Authentication authentication,
                               Model model) {
        String email = resolveEmail(authentication);
        if (email == null) return "redirect:/authen/login";
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/authen/login";
        User user = userOpt.get();
        if (user.isDelete()) return "redirect:/account/orders"; // inactive user cannot review
        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null || !order.getCustomerId().equals(user.getId())) {
            return "redirect:/account/orders";
        }
        if (order.getStatus() != Orders.QueueStatus.COMPLETED) {
            return "redirect:/account/orders";
        }

        // Check if user has already reviewed THIS specific order
        boolean alreadyReviewed = reviewRepository.existsByUser_IdAndOrder_IdAndIsDeleteFalse(user.getId(), orderId);
        if (alreadyReviewed) {
            // Already reviewed this order
            return "redirect:/account/orders/" + orderId + "/review/view";
        }

        if (reviewRequest.getRating() == null || reviewRequest.getRating() < 1 || reviewRequest.getRating() > 5) {
            bindingResult.rejectValue("rating", "invalid", "Rating must be between 1 and 5");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("orderId", orderId);
            model.addAttribute("productName", order.getProduct() != null ? order.getProduct().getName() : ("#" + order.getProductId()));
            return "customer/review-form";
        }
        Review r = new Review();
        r.setOrder(order); // Link review to specific order - allows multiple reviews for same product
        r.setProduct(order.getProduct());
        r.setUser(user);
        r.setRating(reviewRequest.getRating());
        r.setComment(reviewRequest.getComment());
        r.setCreatedBy(user.getId());
        r.setDelete(false);
        reviewRepository.save(r);
        return "redirect:/account/orders"; // could redirect to product detail later
    }

    @PostMapping("/account/orders/{orderId}/review/view")
    @Transactional
    public String updateMyReviewInline(@PathVariable Long orderId,
                                       @RequestParam("reviewId") Long reviewId,
                                       @RequestParam("rating") Integer rating,
                                       @RequestParam(value = "comment", required = false) String comment,
                                       Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) return "redirect:/authen/login";
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/authen/login";
        User user = userOpt.get();
        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null || !order.getCustomerId().equals(user.getId())) return "redirect:/account/orders";
        var rvOpt = reviewRepository.findById(reviewId);
        if (rvOpt.isEmpty()) return "redirect:/account/orders";
        Review mine = rvOpt.get();
        if (mine.getUser() == null || !Objects.equals(mine.getUser().getId(), user.getId())) return "redirect:/account/orders";
        if (mine.isDelete()) return "redirect:/account/orders";
        Date created = mine.getCreatedAt();
        if (created != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(created.toInstant(), java.time.Instant.now());
            if (days > 7) return "redirect:/account/orders";
        }
        if (rating == null || rating < 1 || rating > 5) return "redirect:/account/orders/" + orderId + "/review/view";
        mine.setRating(rating);
        mine.setComment(comment != null ? comment : "");
        reviewRepository.save(mine);
        return "redirect:/account/orders/" + orderId + "/review/view";
    }

    // Simple DTO for binding
    public static class ReviewRequest {
        @Min(1)
        @Max(5)
        private Integer rating;
        private String comment;
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
