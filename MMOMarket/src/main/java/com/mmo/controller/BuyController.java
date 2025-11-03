package com.mmo.controller;

import com.mmo.entity.Product;
import com.mmo.entity.ProductVariant;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.ProductVariantAccountRepository;
import com.mmo.repository.ProductVariantRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/buy")
public class BuyController {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantAccountRepository productVariantAccountRepository;

    public BuyController(ProductRepository productRepository,
                         ProductVariantRepository productVariantRepository,
                         ProductVariantAccountRepository productVariantAccountRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productVariantAccountRepository = productVariantAccountRepository;
    }

    @PostMapping("/preview")
    public String preview(@RequestParam("productId") Long productId,
                          @RequestParam(value = "variantId", required = false) Long variantId,
                          @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
                          Model model) {
        Optional<Product> opt = productRepository.findById(productId);
        if (opt.isEmpty()) {
            model.addAttribute("error", "Product not found");
            model.addAttribute("canConfirm", false);
            return "customer/fragments/buy-modal :: modal";
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
        model.addAttribute("canConfirm", chosenVariantId != null && q >= 1 && (stock == 0 || stock >= q));

        return "customer/fragments/buy-modal :: modal";
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
}
