package com.mmo.service;

import com.mmo.entity.Product;
import com.mmo.entity.ProductVariant;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.ProductVariantRepository;
import com.mmo.repository.ReviewRepository;
import com.mmo.repository.ProductVariantAccountRepository;
import com.mmo.dto.ProductVariantDto;

// NEW: import ShopService and ShopInfoRepository + ShopInfo
import com.mmo.service.ShopService;
import com.mmo.entity.ShopInfo;
import com.mmo.repository.ShopInfoRepository;
import com.mmo.util.TierNameUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    // new repository to access shop meta
    @Autowired
    private ShopInfoRepository shopInfoRepository;

    // new repository to count variant accounts (stock)
    @Autowired
    private ProductVariantAccountRepository productVariantAccountRepository;

    // NEW: reuse seller average rating logic
    @Autowired
    private ShopService shopService;

    // Resolve shop name: prefer ShopInfo.shopName -> seller.fullName -> seller.email -> "Shop"
    private String resolveShopName(Product p) {
        if (p == null || p.getSeller() == null) return "Shop";
        Long userId = p.getSeller().getId();
        if (userId != null) {
            try {
                Optional<ShopInfo> siOpt = shopInfoRepository.findByUserIdAndIsDeleteFalse(userId);
                if (siOpt == null || siOpt.isEmpty()) {
                    siOpt = shopInfoRepository.findByUser_Id(userId);
                }
                if (siOpt != null && siOpt.isPresent()) {
                    ShopInfo si = siOpt.get();
                    if (si.getShopName() != null && !si.getShopName().isBlank()) {
                        return si.getShopName();
                    }
                }
            } catch (Exception ignored) {}
        }

        if (p.getSeller().getFullName() != null && !p.getSeller().getFullName().isBlank()) {
            return p.getSeller().getFullName();
        }
        if (p.getSeller().getEmail() != null && !p.getSeller().getEmail().isBlank()) {
            return p.getSeller().getEmail();
        }
        return "Shop";
    }

    public List<Map<String, Object>> getTopSellingProducts(int limit) {
        List<Product> products = productRepository.findTopSellingProducts(PageRequest.of(0, limit));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> map = new HashMap<>();
            map.put("product", p);

            List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(p.getId());
            Long price = !variants.isEmpty() ? variants.get(0).getPrice() : 0L;
            map.put("price", price);

            Long totalSold = productRepository.getTotalSoldForProduct(p.getId());
            map.put("totalSold", totalSold != null ? totalSold : 0);

            Double avgRating = reviewRepository.getAverageRatingByProduct(p.getId());
            map.put("averageRating", avgRating != null ? avgRating : 0.0);

            // replace old fallback "Unknown Shop"
            map.put("shopName", resolveShopName(p));

            result.add(map);
        }
        return result;
    }

    public List<Map<String, Object>> getProductsByCategory(Long categoryId, Long minPrice, Long maxPrice, String sort, Integer minRating) {
        // Defaults
        if (minPrice == null) minPrice = 0L;
        if (maxPrice == null) maxPrice = 500000L;
        if (sort == null || sort.isBlank()) sort = "newest";
        if (minRating == null) minRating = 0;

        // Fetch unsorted page (we will sort in-memory using computed values)
        PageRequest page = PageRequest.of(0, 20);

        List<Product> products;
        if (categoryId == null || categoryId <= 0) {
            products = productRepository.findAllProducts(minPrice, maxPrice, page);
        } else {
            products = productRepository.findByCategoryId(categoryId, minPrice, maxPrice, page);
        }

        // Build result with aggregates
        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> map = new HashMap<>();
            map.put("product", p);

            List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(p.getId());
            Long price = !variants.isEmpty() ? variants.get(0).getPrice() : 0L;
            map.put("price", price);

            Long totalSold = productRepository.getTotalSoldForProduct(p.getId());
            map.put("totalSold", totalSold != null ? totalSold : 0);

            Double avgRating = reviewRepository.getAverageRatingByProduct(p.getId());
            map.put("averageRating", avgRating != null ? avgRating : 0.0);

            // replace old fallback "Unknown Shop"
            map.put("shopName", resolveShopName(p));

            result.add(map);
        }

        // Filter by minimum rating if requested
        int minRatingVal = (minRating != null) ? minRating : 0;
        if (minRatingVal > 0) {
            final int finalMinRating = minRatingVal;
            result.removeIf(item -> ((Double) item.get("averageRating")) < finalMinRating);
        }

        // In-memory sorting: price, rating, newest
        if ("price-low-to-high".equalsIgnoreCase(sort)) {
            result.sort(Comparator.comparingLong(a -> (Long) a.get("price")));
        } else if ("price-high-to-low".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> Long.compare((Long) b.get("price"), (Long) a.get("price")));
        } else if ("rating".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> Double.compare((Double) b.get("averageRating"), (Double) a.get("averageRating")));
        } else { // newest default - requires product.createdAt
            result.sort((a, b) -> {
                Product pa = (Product) a.get("product");
                Product pb = (Product) b.get("product");
                Comparable ca = (Comparable) (pa.getCreatedAt() != null ? pa.getCreatedAt() : 0);
                Comparable cb = (Comparable) (pb.getCreatedAt() != null ? pb.getCreatedAt() : 0);
                return cb.compareTo(ca);
            });
        }

        return result;
    }

    // New method with pagination support
    public Map<String, Object> getProductsByCategoryWithPagination(Long categoryId, Long minPrice, Long maxPrice,
                                                                     String sort, Integer minRating, int page, int size) {
        // Defaults
        if (minPrice == null) minPrice = 0L;
        if (maxPrice == null) maxPrice = 500000L;
        if (sort == null || sort.isBlank()) sort = "newest";
        if (minRating == null) minRating = 0;

        // Fetch ALL products first (needed for rating filter and sorting)
        PageRequest fetchAll = PageRequest.of(0, 1000); // Fetch up to 1000 products

        List<Product> products;
        if (categoryId == null || categoryId <= 0) {
            products = productRepository.findAllProducts(minPrice, maxPrice, fetchAll);
        } else {
            products = productRepository.findByCategoryId(categoryId, minPrice, maxPrice, fetchAll);
        }

        // Build result with aggregates
        List<Map<String, Object>> allResults = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> map = new HashMap<>();
            map.put("product", p);

            List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(p.getId());
            Long price = !variants.isEmpty() ? variants.get(0).getPrice() : 0L;
            map.put("price", price);

            Long totalSold = productRepository.getTotalSoldForProduct(p.getId());
            map.put("totalSold", totalSold != null ? totalSold : 0);

            Double avgRating = reviewRepository.getAverageRatingByProduct(p.getId());
            map.put("averageRating", avgRating != null ? avgRating : 0.0);

            map.put("shopName", resolveShopName(p));

            allResults.add(map);
        }

        // Filter by minimum rating
        int minRatingVal = (minRating != null) ? minRating : 0;
        if (minRatingVal > 0) {
            final int finalMinRating = minRatingVal;
            allResults.removeIf(item -> ((Double) item.get("averageRating")) < finalMinRating);
        }

        // In-memory sorting
        if ("price-low-to-high".equalsIgnoreCase(sort)) {
            allResults.sort(Comparator.comparingLong(a -> (Long) a.get("price")));
        } else if ("price-high-to-low".equalsIgnoreCase(sort)) {
            allResults.sort((a, b) -> Long.compare((Long) b.get("price"), (Long) a.get("price")));
        } else if ("rating".equalsIgnoreCase(sort)) {
            allResults.sort((a, b) -> Double.compare((Double) b.get("averageRating"), (Double) a.get("averageRating")));
        } else { // newest default
            allResults.sort((a, b) -> {
                Product pa = (Product) a.get("product");
                Product pb = (Product) b.get("product");
                Comparable ca = (Comparable) (pa.getCreatedAt() != null ? pa.getCreatedAt() : 0);
                Comparable cb = (Comparable) (pb.getCreatedAt() != null ? pb.getCreatedAt() : 0);
                return cb.compareTo(ca);
            });
        }

        // Apply pagination manually
        int totalElements = allResults.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalElements);

        List<Map<String, Object>> pagedResults = new ArrayList<>();
        if (startIndex < totalElements) {
            pagedResults = allResults.subList(startIndex, endIndex);
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("content", pagedResults);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("currentPage", page);
        response.put("pageSize", size);

        // Debug logging
        System.out.println("=== PAGINATION DEBUG ===");
        System.out.println("Total Elements: " + totalElements);
        System.out.println("Total Pages: " + totalPages);
        System.out.println("Current Page: " + page);
        System.out.println("Page Size: " + size);
        System.out.println("Paged Results: " + pagedResults.size());
        System.out.println("========================");

        return response;
    }

    public List<Map<String, Object>> getProductsBySeller(Long sellerId) {
        List<Product> products = productRepository.findBySellerId(sellerId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> map = new HashMap<>();
            map.put("product", p);

            List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(p.getId());
            Long price = !variants.isEmpty() ? variants.get(0).getPrice() : 0L;
            map.put("price", price);

            Long totalSold = productRepository.getTotalSoldForProduct(p.getId());
            map.put("totalSold", totalSold != null ? totalSold : 0);

            Double avgRating = reviewRepository.getAverageRatingByProduct(p.getId());
            map.put("averageRating", avgRating != null ? avgRating : 0.0);

            // replace old fallback "Unknown Shop"
            map.put("shopName", resolveShopName(p));

            result.add(map);
        }
        return result;
    }

    public List<Map<String, Object>> getSellerProductsWithFilters(Long sellerId,
                                                                  Long categoryId,
                                                                  Long minPrice,
                                                                  Long maxPrice,
                                                                  String sort,
                                                                  Integer minRating,
                                                                  String query) {
        if (minPrice == null) minPrice = 0L;
        if (maxPrice == null) maxPrice = 500000L;
        if (sort == null || sort.isBlank()) sort = "newest";
        if (minRating == null) minRating = 0;

        PageRequest page = PageRequest.of(0, 20);

        List<Product> products;
        if (categoryId == null || categoryId <= 0) {
            products = productRepository.findBySellerWithPrice(sellerId, minPrice, maxPrice, page);
        } else {
            products = productRepository.findBySellerAndCategoryWithPrice(sellerId, categoryId, minPrice, maxPrice, page);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> map = new HashMap<>();
            map.put("product", p);

            List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(p.getId());
            Long price = !variants.isEmpty() ? variants.get(0).getPrice() : 0L;
            map.put("price", price);

            Long totalSold = productRepository.getTotalSoldForProduct(p.getId());
            map.put("totalSold", totalSold != null ? totalSold : 0);

            Double avgRating = reviewRepository.getAverageRatingByProduct(p.getId());
            map.put("averageRating", avgRating != null ? avgRating : 0.0);

            // replace old fallback "Unknown Shop"
            map.put("shopName", resolveShopName(p));

            result.add(map);
        }

        if (minRating != null && minRating > 0) {
            int finalMin = minRating;
            result.removeIf(item -> ((Double) item.get("averageRating")) < finalMin);
        }

        // Filter by text query (product name) if provided (case-insensitive)
        if (query != null && !query.isBlank()) {
            String qLower = query.toLowerCase().trim();
            result.removeIf(item -> {
                Product prod = (Product) item.get("product");
                String name = prod.getName() != null ? prod.getName().toLowerCase() : "";
                return !name.contains(qLower);
            });
        }

        // In-memory sorting
        if ("price-low-to-high".equalsIgnoreCase(sort)) {
            result.sort(Comparator.comparingLong(a -> (Long) a.get("price")));
        } else if ("price-high-to-low".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> Long.compare((Long) b.get("price"), (Long) a.get("price")));
        } else if ("rating".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> Double.compare((Double) b.get("averageRating"), (Double) a.get("averageRating")));
        } else { // newest
            result.sort((a, b) -> {
                Product pa = (Product) a.get("product");
                Product pb = (Product) b.get("product");
                Comparable ca = (Comparable) (pa.getCreatedAt() != null ? pa.getCreatedAt() : 0);
                Comparable cb = (Comparable) (pb.getCreatedAt() != null ? pb.getCreatedAt() : 0);
                return cb.compareTo(ca);
            });
        }

        return result;
    }

    public Map<String, Object> getProductDetail(Long productId) {
        Optional<Product> opt = productRepository.findById(productId);
        if (opt.isEmpty()) return null;
        Product p = opt.get();

        Map<String, Object> model = new HashMap<>();
        model.put("product", p);

        // NEW: category helpers (avoid ternary in template)
        Long categoryId = (p.getCategory() != null) ? p.getCategory().getId() : 0L;
        String categoryName = (p.getCategory() != null && p.getCategory().getName() != null) ? p.getCategory().getName() : "Unknown";
        model.put("categoryId", categoryId);
        model.put("categoryName", categoryName);

        String placeholder = "https://via.placeholder.com/600x600?text=Product+Image";
        String displayImage = null;
        // Try common getter names without causing compile errors if absent
        for (String getter : List.of("getImageUrl", "getImage", "getThumbnail")) {
            try {
                var m = p.getClass().getMethod(getter);
                Object val = m.invoke(p);
                if (val instanceof String s && !s.isBlank()) {
                    displayImage = s;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (displayImage == null) displayImage = placeholder;
        model.put("displayImage", displayImage);

        // raw variants from DB
        List<ProductVariant> rawVariants = productVariantRepository.findByProductIdAndIsDeleteFalse(p.getId());

        // build DTO list with stock and sold counted from ProductVariantAccount
        List<ProductVariantDto> variantsWithStock = new ArrayList<>();
        for (ProductVariant v : rawVariants) {
            long stock = 0L;
            long sold = 0L;
            try { stock = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(v.getId(), "Available"); } catch (Exception ignored) {}
            try { sold = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(v.getId(), "Sold"); } catch (Exception ignored) {}
            variantsWithStock.add(new ProductVariantDto(v.getId(), v.getVariantName(), v.getPrice(), stock, sold, v.isDelete()));
        }
        model.put("variants", variantsWithStock);

        Long totalSold = productRepository.getTotalSoldForProduct(p.getId());
        model.put("totalSold", totalSold != null ? totalSold : 0);

        Double avgRating = reviewRepository.getAverageRatingByProduct(p.getId());
        model.put("avgRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);

        List<com.mmo.entity.Review> reviews = reviewRepository.findByProductIdAndIsDeleteFalseOrderByCreatedAtDesc(p.getId());
        model.put("reviews", reviews);
        model.put("reviewsCount", reviews != null ? reviews.size() : 0);

        model.put("shopName", resolveShopName(p));

        model.put("relatedProducts", getRelatedProducts(p, 4));

        // Determine a default (lowest) price to show initially (from raw variants)
        Long displayPrice = rawVariants.stream().map(ProductVariant::getPrice).min(Long::compareTo).orElse(0L);
        model.put("displayPrice", displayPrice);

        // --- NEW: compute seller aggregated rating as average across ALL reviews of the seller (weighted by review count) ---
        double sellerAvgVal = 0.0;
        if (p.getSeller() != null && p.getSeller().getId() != null) {
            sellerAvgVal = shopService.getSellerAverageRating(p.getSeller().getId());
        }
        model.put("sellerRating", Math.round(sellerAvgVal * 10.0) / 10.0);

        // NEW: seller total sold (count of Completed transactions across all products of this seller)
        Long shopTotalSold = 0L;
        if (p.getSeller() != null && p.getSeller().getId() != null) {
            Long s = productRepository.getTotalSoldForSeller(p.getSeller().getId());
            shopTotalSold = (s != null) ? s : 0L;
        }
        model.put("shopTotalSold", shopTotalSold);

        // NEW: shopId, shopLevel, tierName based on seller's ShopInfo
        if (p.getSeller() != null && p.getSeller().getId() != null) {
            Long sellerId = p.getSeller().getId();
            try {
                Optional<ShopInfo> siOpt = shopInfoRepository.findByUserIdAndIsDeleteFalse(sellerId);
                if (siOpt == null || siOpt.isEmpty()) {
                    siOpt = shopInfoRepository.findByUser_Id(sellerId);
                }
                if (siOpt != null && siOpt.isPresent()) {
                    ShopInfo si = siOpt.get();
                    model.put("shopId", si.getId());
                    short level = si.getShopLevel() == null ? (short)0 : si.getShopLevel();
                    model.put("shopLevel", level);
                    model.put("tierName", TierNameUtil.getTierName(level));
                }
            } catch (Exception ignored) {}
        }
        // Guarantee defaults if not set by the lookup above
        if (!model.containsKey("tierName")) {
            model.put("shopLevel", (short) 0);
            model.put("tierName", TierNameUtil.getTierName(0));
        }

        return model;
    }

    private List<Map<String,Object>> getRelatedProducts(Product base, int limit) {
        // If no base name, return empty
        if (base == null || base.getName() == null || base.getName().isBlank()) return List.of();

        // Collect candidates: prefer same category, add top selling as extras
        List<Product> candidates = new ArrayList<>();
        if (base.getCategory() != null) {
            candidates.addAll(productRepository.findByCategoryId(
                    base.getCategory().getId(),
                    0L,
                    500000L,
                    PageRequest.of(0, Math.max(limit * 5, 20))
            ));
        }
        // add some top selling products as cross-category candidates
        candidates.addAll(productRepository.findTopSellingProducts(PageRequest.of(0, Math.max(limit * 3, 10))));

        // Deduplicate and remove the base product
        Map<Long, Product> unique = new HashMap<>();
        for (Product p : candidates) {
            if (p == null || p.getId() == null) continue;
            if (p.getId().equals(base.getId())) continue;
            unique.putIfAbsent(p.getId(), p);
        }
        List<Product> candidateList = new ArrayList<>(unique.values());
        if (candidateList.isEmpty()) return List.of();

        // Compute score = 0.7 * nameSimilarity + 0.3 * categoryMatch
        String baseName = base.getName();
        List<Map.Entry<Product, Double>> scored = candidateList.stream().map(p -> {
            double nameSim = computeNameSimilarity(baseName, p.getName());
            double catMatch = (base.getCategory() != null && p.getCategory() != null
                    && base.getCategory().getId().equals(p.getCategory().getId())) ? 1.0 : 0.0;
            double score = nameSim * 0.7 + catMatch * 0.3;
            return new AbstractMap.SimpleEntry<>(p, score);
        }).collect(Collectors.toList());

        // Sort by score desc, then by totalSold desc as tiebreaker
        scored.sort((a, b) -> {
            int cmp = Double.compare(b.getValue(), a.getValue());
            if (cmp != 0) return cmp;
            Long soldA = productRepository.getTotalSoldForProduct(a.getKey().getId());
            Long soldB = productRepository.getTotalSoldForProduct(b.getKey().getId());
            return Long.compare(soldB != null ? soldB : 0L, soldA != null ? soldA : 0L);
        });

        // Build result maps for top 'limit' items
        List<Map<String,Object>> related = new ArrayList<>();
        String placeholder = "https://via.placeholder.com/300x150?text=Related";
        for (Map.Entry<Product, Double> entry : scored) {
            if (related.size() >= limit) break;
            Product rp = entry.getKey();
            Map<String,Object> m = new HashMap<>();
            m.put("product", rp);

            // safe display image detection
            String relatedImg = null;
            for (String getter : List.of("getImageUrl", "getImage", "getThumbnail")) {
                try {
                    var gm = rp.getClass().getMethod(getter);
                    Object val = gm.invoke(rp);
                    if (val instanceof String s && !s.isBlank()) { relatedImg = s; break; }
                } catch (Exception ignored) {}
            }
            if (relatedImg == null) relatedImg = placeholder;
            m.put("displayImage", relatedImg);

            List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(rp.getId());
            Long price = variants.isEmpty() ? 0L : variants.get(0).getPrice();
            m.put("price", price);

            Double ar = reviewRepository.getAverageRatingByProduct(rp.getId());
            m.put("averageRating", ar != null ? Math.round(ar * 10.0) / 10.0 : 0.0);

            Long totalSold = productRepository.getTotalSoldForProduct(rp.getId());
            m.put("totalSold", totalSold != null ? totalSold : 0);

            related.add(m);
        }

        return related;
    }

    // Utility: simple token-based Jaccard similarity for product names (0.0 - 1.0)
    private double computeNameSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        String[] ta = a.toLowerCase().split("\\W+");
        String[] tb = b.toLowerCase().split("\\W+");
        Set<String> sa = Arrays.stream(ta).filter(s -> !s.isBlank()).collect(Collectors.toCollection(HashSet::new));
        Set<String> sb = Arrays.stream(tb).filter(s -> !s.isBlank()).collect(Collectors.toCollection(HashSet::new));
        if (sa.isEmpty() || sb.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(sa);
        intersection.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return (double) intersection.size() / (double) union.size();
    }
}
