package com.mmo.controller.customer;

import com.mmo.entity.Product;
import com.mmo.entity.ProductVariant;
import com.mmo.entity.Transaction;
import com.mmo.entity.User;
import com.mmo.repository.TransactionRepository;
import com.mmo.service.ProductService;
import com.mmo.service.ProductVariantService;
import com.mmo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Calendar;
import java.util.Date;

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
    private TransactionRepository transactionRepository;

    @GetMapping("/checkout")
    public String showCheckout(Model model, Authentication authentication) {
        User customer = userService.findByEmail(authentication.getName());
        model.addAttribute("customer", customer);
        return "customer/checkout";
    }

    @PostMapping("/checkout/process")
    @Transactional
    public String processCheckout(@RequestParam Long productId,
                                  @RequestParam Long variantId,
                                  @RequestParam int quantity,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            User customer = userService.findByEmail(authentication.getName());
            ProductVariant variant = productVariantService.findById(variantId);
            Product product = productService.findById(productId);

            long totalPrice = variant.getPrice() * quantity;
            long commission = (long)(totalPrice * 0.05);

            // Kiểm tra coins
            if (customer.getCoins() < totalPrice) {
                redirectAttributes.addFlashAttribute("error", "Số dư Coin không đủ!");
                return "redirect:/customer/checkout";
            }

            // Kiểm tra tồn kho
            if (variant.getStock() < quantity) {
                redirectAttributes.addFlashAttribute("error", "Không đủ hàng!");
                return "redirect:/customer/checkout";
            }

            if (!"Active".equals(variant.getStatus())) {
                redirectAttributes.addFlashAttribute("error", "Sản phẩm không khả dụng!");
                return "redirect:/customer/checkout";
            }

            // DISABLE TRIGGERS
            transactionRepository.disableTriggers();

            // Trừ coins customer (thay trigger)
            customer.setCoins(customer.getCoins() - totalPrice);
            userService.save(customer);

            // Tạo transaction
            Transaction transaction = new Transaction();
            transaction.setCustomerId(customer.getId());
            transaction.setSellerId(product.getSeller().getId());
            transaction.setProductId(productId);
            transaction.setVariantId(variantId);
            transaction.setAmount(totalPrice);
            transaction.setCommission(commission);
            transaction.setCoinsUsed(totalPrice);
            transaction.setStatus("Held");
            transaction.setCreatedBy(customer.getId());
            transaction.setDelete(false);

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 3);
            transaction.setEscrowReleaseDate(cal.getTime());

            // Giảm tồn kho
            variant.setStock(variant.getStock() - quantity);
            if (variant.getStock() <= 0) {
                variant.setStatus("Inactive");
            }
            productVariantService.save(variant);

            // Lưu transaction
            transactionRepository.save(transaction);

            // ENABLE TRIGGERS LẠI
            transactionRepository.enableTriggers();

            redirectAttributes.addFlashAttribute("message", "Mua hàng thành công!");
            return "redirect:/customer/order-success";

        } catch (Exception e) {
            // ENABLE TRIGGERS nếu lỗi
            try {
                transactionRepository.enableTriggers();
            } catch (Exception ex) {}

            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "redirect:/customer/checkout";
        }
    }

    @GetMapping("/order-success")
    public String orderSuccess(Model model, Authentication authentication) {
        User customer = userService.findByEmail(authentication.getName());
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