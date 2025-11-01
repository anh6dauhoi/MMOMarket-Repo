package com.mmo.service;

import com.mmo.entity.Product;
import com.mmo.entity.User;
import com.mmo.entity.Wishlist;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.WishlistRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;

    public WishlistService(WishlistRepository wishlistRepository, ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public boolean addToWishlist(User user, Long productId) {
        if (user == null || productId == null) return false;
        if (wishlistRepository.existsByUserIdAndProductId(user.getId(), productId)) return false;

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return false;

        // New: server-side check — if product stock is present and zero, do not allow adding to wishlist
        try {
            Integer stock = null;
            try {
                // defensive: product.getStock() might be Integer or int; handle via reflection if necessary
                stock = (Integer) Product.class.getMethod("getStock").invoke(product);
            } catch (NoSuchMethodException nsme) {
                // If product has no getStock(), fall back to allow (can't determine)
                stock = null;
            } catch (Exception ignored) {
                stock = null;
            }
            if (stock != null && stock <= 0) {
                return false;
            }
        } catch (Exception ignored) { /* keep going if reflection failed */ }

        try {
            Wishlist w = new Wishlist();
            w.setUser(user);
            w.setProduct(product);
            wishlistRepository.save(w);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getWishlistItems(User user) {
        if (user == null) return Collections.emptyList();
        List<Wishlist> rows = wishlistRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Wishlist w : rows) {
            Product p = w.getProduct();
            if (p == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("id", w.getId());
            m.put("product", p);
            m.put("createdAt", w.getCreatedAt());
            items.add(m);
        }
        return items;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWishlistItemsPaged(User user, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        if (user == null) {
            result.put("items", Collections.emptyList());
            result.put("page", 1);
            result.put("size", size);
            result.put("totalPages", 0);
            result.put("totalElements", 0L);
            return result;
        }
        int p = Math.max(1, page);
        PageRequest pr = PageRequest.of(p - 1, size);
        Page<Wishlist> pageData = wishlistRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId(), pr);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Wishlist w : pageData.getContent()) {
            Product prod = w.getProduct();
            if (prod == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("id", w.getId());
            m.put("product", prod);
            m.put("createdAt", w.getCreatedAt());
            items.add(m);
        }

        result.put("items", items);
        result.put("page", p);
        result.put("size", size);
        result.put("totalPages", pageData.getTotalPages());
        result.put("totalElements", pageData.getTotalElements());
        return result;
    }

    @Transactional
    public void remove(User user, Long productId) {
        if (user == null || productId == null) return;
        wishlistRepository.deleteByUserIdAndProductId(user.getId(), productId);
    }
}
