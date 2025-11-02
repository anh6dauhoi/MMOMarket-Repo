package com.mmo.controller;

import com.mmo.entity.Review;
import com.mmo.entity.User;
import com.mmo.service.ReviewService;
import com.mmo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserService userService;

    // Thêm review mới
    @PostMapping("/add")
    public String addReview(@RequestParam Long productId,
                            @RequestParam Integer rating,
                            @RequestParam String comment,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email);

            // Kiểm tra user đã review chưa
            if (reviewService.hasUserReviewedProduct(productId, user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Bạn đã đánh giá sản phẩm này rồi!");
                return "redirect:/seller/products/details/" + productId;
            }

            // Tạo review mới
            Review review = new Review();
            review.setProductId(productId);
            review.setUserId(user.getId());
            review.setRating(rating);
            review.setComment(comment);
            review.setCreatedBy(user.getId());

            reviewService.save(review);
            redirectAttributes.addFlashAttribute("message", "Đánh giá của bạn đã được gửi thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }

        return "redirect:/customer/products/" + productId;
    }

    // Cập nhật review
    @PostMapping("/update/{id}")
    public String updateReview(@PathVariable Long id,
                               @RequestParam Long productId,
                               @RequestParam Integer rating,
                               @RequestParam String comment,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email);

            Review reviewDetails = new Review();
            reviewDetails.setRating(rating);
            reviewDetails.setComment(comment);

            reviewService.update(id, reviewDetails, user.getId());
            redirectAttributes.addFlashAttribute("message", "Cập nhật đánh giá thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }

        return "redirect:/seller/products/details/" + productId;
    }

    // Xóa review
    @PostMapping("/delete/{id}")
    public String deleteReview(@PathVariable Long id,
                               @RequestParam Long productId,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email);

            reviewService.softDelete(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Xóa đánh giá thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa: " + e.getMessage());
        }

        return "redirect:/seller/products/details/" + productId;
    }
}