package com.mmo.controller.customer;

import com.mmo.entity.*;
import com.mmo.repository.TransactionRepository;
import com.mmo.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/customer")
public class CheckoutController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService productVariantService;

    @Autowired
    private ProductVariantAccountService productVariantAccountService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OrdersService orderQueueService;

    @GetMapping("/checkout")
    public String showCheckout(Model model, Authentication authentication) {
        User customer = userService.findByEmail(authentication.getName());
        model.addAttribute("customer", customer);
        return "customer/checkout";
    }

    @PostMapping("/checkout/process")
    public String processCheckout(@RequestParam Long productId,
                                  @RequestParam Long variantId,
                                  @RequestParam int quantity,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            // 1. Lấy thông tin cơ bản
            User customer = userService.findByEmail(authentication.getName());
            ProductVariant variant = productVariantService.findById(variantId);
            Product product = productService.findById(productId);

            // 2. Validation cơ bản
            if (customer == null || variant == null || product == null) {
                redirectAttributes.addFlashAttribute("error", "Thông tin không hợp lệ!");
                return "redirect:/customer/checkout";
            }

            // 3. Kiểm tra stock (nhanh)
            long availableStock = productVariantAccountService.countAvailableAccounts(variantId);
            if (availableStock < quantity) {
                redirectAttributes.addFlashAttribute("error",
                        "Không đủ hàng! Chỉ còn " + availableStock + " sản phẩm.");
                return "redirect:/customer/checkout";
            }

            // 4. Tính toán
            long totalPrice = variant.getPrice() * quantity;

            // 5. Kiểm tra số dư
            if (customer.getCoins() < totalPrice) {
                redirectAttributes.addFlashAttribute("error",
                        String.format("Số dư Coin không đủ! Bạn cần %,d coins nhưng chỉ có %,d coins.",
                                totalPrice, customer.getCoins()));
                return "redirect:/customer/checkout";
            }

            // 6. TẠO ORDER QUEUE thay vì xử lý ngay
            Orders order = orderQueueService.createOrderQueue(
                    customer.getId(),
                    productId,
                    variantId,
                    quantity,
                    totalPrice
            );

            // 7. Redirect sang trang "Đang xử lý"
            return "redirect:/customer/order-processing?orderId=" + order.getId();

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "redirect:/customer/checkout";
        }
    }

    @GetMapping("/api/order-status/{orderId}")
    @ResponseBody
    public Map<String, Object> checkOrderStatus(@PathVariable Long orderId,
                                                Authentication authentication) {
        User customer = userService.findByEmail(authentication.getName());
        Orders order = orderQueueService.getOrderByCustomer(orderId, customer.getId());

        if (order == null) {
            return Map.of("error", "Order not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", order.getStatus().toString());
        response.put("orderId", order.getId());

        if (order.getStatus() == Orders.QueueStatus.COMPLETED) {
            response.put("transactionId", order.getTransactionId());
        } else if (order.getStatus() == Orders.QueueStatus.FAILED) {
            response.put("error", order.getErrorMessage());
        }

        return response;
    }

    @GetMapping("/order-processing")
    public String orderProcessing(@RequestParam Long orderId,
                                  Model model,
                                  Authentication authentication) {
        User customer = userService.findByEmail(authentication.getName());

        // Kiểm tra order có thuộc về customer không
        Orders order = orderQueueService.getOrderByCustomer(orderId, customer.getId());
        if (order == null) {
            return "redirect:/customer/checkout";
        }

        model.addAttribute("customer", customer);
        model.addAttribute("orderId", orderId);
        return "customer/order-processing";
    }

    @GetMapping("/order-success")
    public String orderSuccess(@RequestParam(required = false) Long transactionId,
                               Model model,
                               Authentication authentication) {
        User customer = userService.findByEmail(authentication.getName());

        if (transactionId != null) {
            // Load transaction và accounts
            Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
            if (transaction != null) {
                Product product = productService.findById(transaction.getProductId());
                ProductVariant variant = productVariantService.findById(transaction.getVariantId());
                List<ProductVariantAccount> accounts = productVariantAccountService
                        .getAccountsByTransactionId(transactionId);

                model.addAttribute("transaction", transaction);
                model.addAttribute("product", product);
                model.addAttribute("variant", variant);
                model.addAttribute("seller", product.getSeller());
                model.addAttribute("accountsToBuy", accounts);
                model.addAttribute("transactionId", transactionId);
                model.addAttribute("quantity", accounts.size());
                model.addAttribute("totalAmount", transaction.getAmount());
                model.addAttribute("purchaseDate", transaction.getCreatedAt());
            }
        }

        model.addAttribute("customer", customer);
        return "customer/order-success";
    }

    @GetMapping("/orders")
    public String myOrders(Model model, Authentication authentication) {
        User customer = userService.findByEmail(authentication.getName());
        model.addAttribute("customer", customer);
        return "customer/my-orders";
    }
}