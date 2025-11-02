package com.mmo.controller.seller;

import com.mmo.entity.*;
import com.mmo.service.ProductService;
import com.mmo.service.ProductVariantService;
import com.mmo.service.CategoryService;
import com.mmo.service.ReviewService;
import com.mmo.service.UserService;
import com.mmo.service.ProductVariantAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.ArrayList;

@Controller
@RequestMapping("/seller")
public class SellerProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService productVariantService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ProductVariantAccountService accountService;

    // Hiển thị danh sách sản phẩm của seller
    @GetMapping("/products")
    public String manageProducts(Model model, Authentication authentication,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String search) {
        String email = authentication.getName();
        User seller = userService.findByEmail(email);

        List<Product> products;
        if (search != null && !search.isEmpty()) {
            products = productService.searchProductsBySeller(seller.getId(), search);
        } else if (category != null && !category.isEmpty()) {
            products = productService.findBySellerAndCategory(seller.getId(), Long.parseLong(category));
        } else {
            products = productService.findBySellerId(seller.getId());
        }

        List<Category> categories = categoryService.findAll();

        model.addAttribute("products", products);
        model.addAttribute("categories", categories);
        model.addAttribute("seller", seller);
        model.addAttribute("activePage", "products");

        return "seller/products/manage-products";
    }

    // Hiển thị form thêm sản phẩm mới
    @GetMapping("/products/new")
    public String showAddProductForm(Model model) {
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);
        model.addAttribute("product", new Product());
        return "seller/products/details";
    }

    // Xử lý thêm sản phẩm mới
    @PostMapping("/products/add")
    public String addProduct(@ModelAttribute Product product,
                             @RequestParam("imageFile") MultipartFile imageFile,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();

            User seller = userService.findByEmail(email);

            product.setSeller(seller);

            // Xử lý upload ảnh
            if (!imageFile.isEmpty()) {
                System.out.println("File name: " + imageFile.getOriginalFilename());
                String imagePath = productService.saveImage(imageFile);
                product.setImage(imagePath);
                System.out.println("Image saved: " + imagePath);
            } else {
                System.out.println("Không có file ảnh");
            }

            Product savedProduct = productService.save(product);

            redirectAttributes.addFlashAttribute("message", "Add product successfully!");
            return "redirect:/seller/products";
        } catch (Exception e) {
            e.printStackTrace(); // In ra full stack trace
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/seller/products/new";
        }
    }

    // Hiển thị form chỉnh sửa sản phẩm
    @GetMapping("/products/edit/{id}")
    public String showEditProductForm(@PathVariable Long id, Model model, Authentication authentication) {
        String email = authentication.getName();
        User seller = userService.findByEmail(email);

        Product product = productService.findById(id);

        // Kiểm tra quyền sở hữu
        if (!product.getSeller().getId().equals(seller.getId())) {
            return "redirect:/seller/products";
        }

        List<Category> categories = categoryService.findAll();
        List<ProductVariant> variants = productVariantService.findByProductId(id);

        model.addAttribute("product", product);
        model.addAttribute("categories", categories);
        model.addAttribute("variants", variants);

        return "seller/products/details";
    }

    @GetMapping("/products/get/{id}")
    @ResponseBody
    public Map<String, Object> getProduct(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        User seller = userService.findByEmail(email);
        Product product = productService.findById(id);

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        // Trả về Map thay vì Product để tránh circular reference
        Map<String, Object> response = new HashMap<>();
        response.put("id", product.getId());
        response.put("name", product.getName());
        response.put("description", product.getDescription());
        response.put("image", product.getImage());

        Map<String, Object> categoryData = new HashMap<>();
        categoryData.put("id", product.getCategory().getId());
        categoryData.put("name", product.getCategory().getName());
        response.put("category", categoryData);

        return response;
    }

    // Xử lý cập nhật sản phẩm
    @PostMapping("/products/update/{id}")
    public String updateProduct(@PathVariable Long id,
                                @ModelAttribute Product product,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User seller = userService.findByEmail(email);

            Product existingProduct = productService.findById(id);

            // Kiểm tra quyền sở hữu
            if (!existingProduct.getSeller().getId().equals(seller.getId())) {
                return "redirect:/seller/products";
            }

            existingProduct.setName(product.getName());
            existingProduct.setDescription(product.getDescription());
            existingProduct.setCategory(product.getCategory());

            // Xử lý upload ảnh mới nếu có
            if (imageFile != null && !imageFile.isEmpty()) {
                String imagePath = productService.saveImage(imageFile);
                existingProduct.setImage(imagePath);
            }

            productService.save(existingProduct);
            redirectAttributes.addFlashAttribute("message", "Cập nhật sản phẩm thành công!");
            return "redirect:/seller/products";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/seller/products/details/" + id;
        }
    }

    // Xóa sản phẩm (soft delete)
    @PostMapping("/products/delete/{id}")
    public String deleteProduct(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User seller = userService.findByEmail(email);

            Product product = productService.findById(id);

            // Kiểm tra quyền sở hữu
            if (!product.getSeller().getId().equals(seller.getId())) {
                return "redirect:/seller/products";
            }

            productService.softDelete(id, seller.getId());
            redirectAttributes.addFlashAttribute("message", "Xóa sản phẩm thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa: " + e.getMessage());
        }
        return "redirect:/seller/products";
    }

    // Quản lý variants
    @PostMapping("/products/{productId}/variants/add")
    public String addVariant(@PathVariable Long productId,
                             @RequestParam String variantName,
                             @RequestParam Long price,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            // Validation
            if (variantName == null || variantName.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Variant name cannot be empty!");
                return "redirect:/seller/products/details/" + productId;
            }

            if (price == null || price < 1) {
                redirectAttributes.addFlashAttribute("error", "Price must be at least 1 coin!");
                return "redirect:/seller/products/details/" + productId;
            }

            if (price > 999999999) {
                redirectAttributes.addFlashAttribute("error", "Price cannot exceed 999,999,999 coins!");
                return "redirect:/seller/products/details/" + productId;
            }

            String email = authentication.getName();
            User seller = userService.findByEmail(email);
            Product product = productService.findById(productId);

            if (!product.getSeller().getId().equals(seller.getId())) {
                return "redirect:/seller/products";
            }

            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setVariantName(variantName.trim());
            variant.setPrice(price);
            variant.setStatus("Pending");
            variant.setCreatedBy(seller.getId());
            variant.setDelete(false);

            productVariantService.save(variant);

            redirectAttributes.addFlashAttribute("message", "Variant added successfully!");
            return "redirect:/seller/products/details/" + productId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/seller/products/details/" + productId;
        }
    }

    @PostMapping("/products/variants/update/{variantId}")
    public String updateVariant(@PathVariable Long variantId,
                                @RequestParam String variantName,
                                @RequestParam Long price,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            // Validation
            if (variantName == null || variantName.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Variant name cannot be empty!");
                return "redirect:/seller/products";
            }

            if (price == null || price < 1) {
                redirectAttributes.addFlashAttribute("error", "Price must be at least 1 coin!");
                return "redirect:/seller/products";
            }

            if (price > 999999999) {
                redirectAttributes.addFlashAttribute("error", "Price cannot exceed 999,999,999 coins!");
                return "redirect:/seller/products";
            }

            String email = authentication.getName();
            User seller = userService.findByEmail(email);

            ProductVariant variant = productVariantService.findById(variantId);
            Product product = productService.findById(variant.getProduct().getId());

            if (!product.getSeller().getId().equals(seller.getId())) {
                return "redirect:/seller/products";
            }

            variant.setVariantName(variantName.trim());
            variant.setPrice(price);
            variant.setUpdatedAt(new Date());

            productVariantService.save(variant);

            redirectAttributes.addFlashAttribute("message", "Variant updated successfully!");
            return "redirect:/seller/products/details/" + product.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/seller/products";
        }
    }

    @PostMapping("/products/{productId}/variants/delete/{variantId}")
    public String deleteVariant(@PathVariable Long productId,
                                @PathVariable Long variantId,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User seller = userService.findByEmail(email);

            productVariantService.softDelete(variantId, seller.getId());
            redirectAttributes.addFlashAttribute("message", "Xóa biến thể thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa: " + e.getMessage());
        }
        return "redirect:/seller/products/details/" + productId;
    }

    @GetMapping("/products/variants/get/{variantId}")
    @ResponseBody
    public Map<String, Object> getVariant(@PathVariable Long variantId, Authentication authentication) {
        String email = authentication.getName();
        User seller = userService.findByEmail(email);

        ProductVariant variant = productVariantService.findById(variantId);
        Product product = productService.findById(variant.getProduct().getId());

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", variant.getId());
        response.put("variantName", variant.getVariantName());
        response.put("price", variant.getPrice());

        return response;
    }

    @GetMapping("/products/details/{id}")
    public String viewProductDetails(@PathVariable Long id, Model model, Authentication authentication) {
        String email = authentication.getName();
        User currentUser = userService.findByEmail(email);

        Product product = productService.findById(id);
        List<ProductVariant> variants = productVariantService.findByProductId(id);

        // Lấy thông tin seller
        User seller = product.getSeller();

        // Lấy reviews và thống kê
        List<Review> reviews = reviewService.findByProductId(id);
        double avgRating = reviewService.getAverageRating(id);
        long totalReviews = reviewService.getTotalReviews(id);
        Map<Integer, Long> reviewStats = reviewService.getReviewStatsByRating(id);

        // Kiểm tra user đã review chưa
        boolean hasReviewed = reviewService.hasUserReviewedProduct(id, currentUser.getId());
        Review userReview = reviewService.getUserReviewForProduct(id, currentUser.getId());

        // Tính toán thống kê bán hàng (TODO: implement từ Transactions)
        int totalSold = 0;

        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        model.addAttribute("seller", seller);
        model.addAttribute("reviews", reviews);
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("reviewStats", reviewStats);
        model.addAttribute("hasReviewed", hasReviewed);
        model.addAttribute("userReview", userReview);
        model.addAttribute("totalSold", totalSold);
        model.addAttribute("currentUser", currentUser);

        return "seller/products/product-details";
    }

    // Upload accounts từ Excel
    @PostMapping("/products/variants/{variantId}/accounts/add")
    public String addAccounts(@PathVariable Long variantId,
                              @RequestParam("accountFile") MultipartFile file,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User seller = userService.findByEmail(email);

            // Kiểm tra quyền sở hữu variant
            ProductVariant variant = productVariantService.findById(variantId);
            Product product = productService.findById(variant.getProduct().getId());

            if (!product.getSeller().getId().equals(seller.getId())) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized");
                return "redirect:/seller/products";
            }

            // Import accounts từ Excel
            int count = accountService.importAccountsFromExcel(file, variantId, seller.getId());

            // Cập nhật stock
            productVariantService.updateStock(variantId);

            redirectAttributes.addFlashAttribute("message",
                    "Successfully added " + count + " accounts!");

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }

        return "redirect:/seller/products/details/" +
                productVariantService.findById(variantId).getProduct().getId();
    }

    // Restore variant đã xóa
    @PostMapping("/products/variants/restore/{variantId}")
    public String restoreVariant(@PathVariable Long variantId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User seller = userService.findByEmail(email);

            ProductVariant variant = productVariantService.findById(variantId);
            Product product = productService.findById(variant.getProduct().getId());

            if (!product.getSeller().getId().equals(seller.getId())) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized");
                return "redirect:/seller/products";
            }

            // Restore variant
            variant.setDelete(false);
            variant.setDeletedBy(null);
            variant.setUpdatedAt(new Date());
            productVariantService.save(variant);

            redirectAttributes.addFlashAttribute("message", "Variant restored successfully!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }

        return "redirect:/seller/products/details/" +
                productVariantService.findById(variantId).getProduct().getId();
    }

    // Xem danh sách accounts của variant
    @GetMapping("/products/variants/{variantId}/accounts")
    public String viewVariantAccounts(@PathVariable Long variantId,
                                      Model model,
                                      Authentication authentication) {
        String email = authentication.getName();
        User seller = userService.findByEmail(email);

        // Kiểm tra quyền
        ProductVariant variant = productVariantService.findById(variantId);
        Product product = productService.findById(variant.getProduct().getId());

        if (!product.getSeller().getId().equals(seller.getId())) {
            return "redirect:/seller/products";
        }

        // Lấy danh sách accounts
        List<ProductVariantAccount> accounts = accountService.getAccountsByVariantId(variantId);

// Đếm available và sold
        long availableCount = accounts.stream()
                .filter(acc -> acc.getStatus() == ProductVariantAccount.AccountStatus.Available)
                .count();
        long soldCount = accounts.stream()
                .filter(acc -> acc.getStatus() == ProductVariantAccount.AccountStatus.Sold)
                .count();

        model.addAttribute("variant", variant);
        model.addAttribute("product", product);
        model.addAttribute("accounts", accounts);
        model.addAttribute("availableCount", availableCount);  // THÊM
        model.addAttribute("soldCount", soldCount);            // THÊM
        model.addAttribute("seller", seller);

        return "seller/products/variant-accounts";
    }

    // Xóa account
    @PostMapping("/products/variants/accounts/delete/{accountId}")
    public String deleteAccount(@PathVariable Long accountId,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String email = authentication.getName();
            User seller = userService.findByEmail(email);

            ProductVariantAccount account = accountService.findById(accountId);
            ProductVariant variant = productVariantService.findById(account.getVariantId());
            Product product = productService.findById(variant.getProduct().getId());

            // Kiểm tra quyền
            if (!product.getSeller().getId().equals(seller.getId())) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized");
                return "redirect:/seller/products";
            }

            // Chỉ được xóa account chưa bán (Available)
            if (account.getStatus() == ProductVariantAccount.AccountStatus.Sold) {
                redirectAttributes.addFlashAttribute("error", "Cannot delete sold account!");
                return "redirect:/seller/products/variants/" + variant.getId() + "/accounts";
            }

            // Soft delete
            accountService.softDelete(accountId, seller.getId());

            // Cập nhật stock
            productVariantService.updateStock(variant.getId());

            redirectAttributes.addFlashAttribute("message", "Account deleted successfully!");
            return "redirect:/seller/products/variants/" + variant.getId() + "/accounts";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/seller/products";
        }
    }
}