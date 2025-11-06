package com.mmo.controller;

import com.mmo.entity.Product;
import com.mmo.entity.ProductVariant;
import com.mmo.entity.Orders;
import com.mmo.mq.BuyAccountPublisher;
import com.mmo.repository.OrdersRepository;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.ProductVariantAccountRepository;
import com.mmo.repository.ProductVariantRepository;
import com.mmo.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/buy")
public class BuyController {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantAccountRepository productVariantAccountRepository;
    private final UserRepository userRepository;
    private final OrdersRepository ordersRepository;
    private final BuyAccountPublisher buyAccountPublisher;

    public BuyController(ProductRepository productRepository,
                         ProductVariantRepository productVariantRepository,
                         ProductVariantAccountRepository productVariantAccountRepository,
                         UserRepository userRepository,
                         OrdersRepository ordersRepository,
                         BuyAccountPublisher buyAccountPublisher) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productVariantAccountRepository = productVariantAccountRepository;
        this.userRepository = userRepository;
        this.ordersRepository = ordersRepository;
        this.buyAccountPublisher = buyAccountPublisher;
    }

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

    @PostMapping("/preview")
    public String preview(@RequestParam("productId") Long productId,
                          @RequestParam(value = "variantId", required = false) Long variantId,
                          @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
                          Authentication authentication,
                          Model model) {
        Optional<Product> opt = productRepository.findById(productId);
        if (opt.isEmpty()) {
            model.addAttribute("error", "Product not found");
            model.addAttribute("canConfirm", false);
            return "customer/buy-modal :: modal";
        }
        Product product = opt.get();

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(product.getId());
        ProductVariant chosen = null;
        if (variantId != null) {
            Optional<ProductVariant> v = productVariantRepository.findById(variantId);
            if (v.isPresent() && v.get().getProduct().getId().equals(product.getId())) {
                chosen = v.get();
            }
        }
        if (chosen == null && !variants.isEmpty()) {
            chosen = variants.get(0);
        }

        long unitPrice = 0L;
        String variantName = "Default";
        Long chosenVariantId = null;
        long stock = 0L;
        if (chosen != null) {
            unitPrice = chosen.getPrice() != null ? chosen.getPrice() : 0L;
            variantName = StringUtils.hasText(chosen.getVariantName()) ? chosen.getVariantName() : variantName;
            chosenVariantId = chosen.getId();
            stock = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(chosen.getId(), "Available");
        }

        int q = (quantity != null) ? quantity : 1;
        if (q < 1) q = 1;
        if (stock > 0 && q > stock) q = (int) stock;
        long total = unitPrice * q;

        // Resolve current user coins
        Long userCoins = null;
        boolean hasEnoughCoins = false;
        String email = resolveEmail(authentication);
        if (email != null) {
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                userCoins = userOpt.get().getCoins() == null ? 0L : userOpt.get().getCoins();
                hasEnoughCoins = userCoins >= total;
                model.addAttribute("customerId", userOpt.get().getId());
            }
        }

        // image fallback (kept for future use if needed)
        String displayImage = null;
        for (String getter : new String[]{"getImageUrl", "getImage", "getThumbnail"}) {
            try {
                var m = product.getClass().getMethod(getter);
                Object val = m.invoke(product);
                if (val instanceof String s && !s.isBlank()) { displayImage = s; break; }
            } catch (Exception ignored) {}
        }
        if (displayImage == null) displayImage = "/images/home.jpg";

        model.addAttribute("product", product);
        model.addAttribute("variantId", chosenVariantId);
        model.addAttribute("variantName", variantName);
        model.addAttribute("unitPrice", unitPrice);
        model.addAttribute("quantity", q);
        model.addAttribute("total", total);
        model.addAttribute("stock", stock);
        model.addAttribute("displayImage", displayImage);
        model.addAttribute("userCoins", userCoins == null ? 0L : userCoins);
        model.addAttribute("hasEnoughCoins", hasEnoughCoins);
        model.addAttribute("canConfirm", chosenVariantId != null && q >= 1 && (stock == 0 || stock >= q) && hasEnoughCoins);

        return "customer/buy-modal :: modal";
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam("productId") Long productId,
                          @RequestParam("variantId") Long variantId,
                          @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
                          RedirectAttributes ra) {
        int q = (quantity != null) ? quantity : 1;
        if (q < 1) q = 1;

        Optional<Product> opt = productRepository.findById(productId);
        if (opt.isEmpty()) {
            ra.addAttribute("buyConfirmed", 0);
            ra.addAttribute("msg", "Product not found");
            return "redirect:/productdetail?id=" + productId;
        }
        Optional<ProductVariant> optV = productVariantRepository.findById(variantId);
        if (optV.isEmpty() || !optV.get().getProduct().getId().equals(productId)) {
            ra.addAttribute("buyConfirmed", 0);
            ra.addAttribute("msg", "Invalid variant");
            return "redirect:/productdetail?id=" + productId;
        }
        long stock = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(variantId, "Available");
        if (stock <= 0) {
            ra.addAttribute("buyConfirmed", 0);
            ra.addAttribute("msg", "Out of stock");
            return "redirect:/productdetail?id=" + productId;
        }
        if (q > stock) q = (int) stock;

        ra.addAttribute("buyConfirmed", 1);
        ra.addAttribute("qty", q);
        return "redirect:/productdetail?id=" + productId;
    }

    // New: create order and push to MQ; respond quickly for client redirect
    @PostMapping("/place")
    @ResponseBody
    public ResponseEntity<?> place(@RequestParam("productId") Long productId,
                                   @RequestParam("variantId") Long variantId,
                                   @RequestParam(value = "quantity", defaultValue = "1") Integer quantity,
                                   Authentication authentication) {
        int q = quantity != null && quantity > 0 ? quantity : 1;
        try {
            String email = resolveEmail(authentication);
            if (email == null) return ResponseEntity.status(401).body("Unauthorized");
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) return ResponseEntity.status(401).body("Unauthorized");

            var productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) return ResponseEntity.badRequest().body("Product not found");
            var variantOpt = productVariantRepository.findById(variantId);
            if (variantOpt.isEmpty() || !variantOpt.get().getProduct().getId().equals(productId))
                return ResponseEntity.badRequest().body("Invalid variant");

            // Use current price; cap quantity by current stock snapshot (final validation occurs in consumer)
            long stock = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(variantId, "Available");
            if (stock <= 0) return ResponseEntity.badRequest().body("Out of stock");
            if (q > stock) q = (int) stock;
            long unitPrice = variantOpt.get().getPrice() == null ? 0L : variantOpt.get().getPrice();
            long total = unitPrice * q;

            Orders o = new Orders();
            o.setRequestId(UUID.randomUUID().toString());
            o.setCustomerId(userOpt.get().getId());
            o.setProductId(productId);
            o.setVariantId(variantId);
            o.setQuantity((long) q);
            o.setTotalPrice(total);
            o.setStatus(Orders.QueueStatus.PENDING);
            o.setCreatedAt(new Date());
            ordersRepository.save(o);

            // Publish to MQ
            buyAccountPublisher.publish(o.getId());

            return ResponseEntity.ok(java.util.Map.of(
                    "ok", true,
                    "orderId", o.getId()
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }
}
