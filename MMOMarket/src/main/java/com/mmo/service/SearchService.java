package com.mmo.service;

import com.mmo.entity.SearchHistory;
import com.mmo.entity.User;
import com.mmo.entity.Product;
import com.mmo.entity.ShopInfo;
import com.mmo.repository.SearchHistoryRepository;
import com.mmo.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;
    private final EntityManager em;

    public SearchService(SearchHistoryRepository searchHistoryRepository,
                         UserRepository userRepository,
                         EntityManager em) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
        this.em = em;
    }

    @Transactional
    public void saveSearch(User user, String query) {
        if (user == null || query == null || query.trim().isEmpty()) return;

        String q = query.trim();

        // if identical query already exists for this user, delete it so the new insert becomes the latest
        try {
            Optional<SearchHistory> existing = searchHistoryRepository.findTopByUserAndSearchQueryIgnoreCase(user, q);
            existing.ifPresent(searchHistoryRepository::delete);
        } catch (Throwable ex) {
            log.debug("Failed to check/delete existing search history entry: {}", ex.toString());
        }

        SearchHistory sh = new SearchHistory();
        sh.setUser(user);
        sh.setSearchQuery(q);
        // save immediately (DB will set created_at default)
        searchHistoryRepository.saveAndFlush(sh);

        // keep only 10 most recent
        List<SearchHistory> all = searchHistoryRepository.findByUserOrderByCreatedAtDesc(user);
        if (all.size() > 10) {
            List<Long> toDelete = all.subList(10, all.size()).stream()
                    .map(SearchHistory::getId).collect(Collectors.toList());
            searchHistoryRepository.deleteAllById(toDelete);
        }
    }

    @Transactional(readOnly = true)
    public List<SearchHistory> recentSearches(User user) {
        if (user == null) return Collections.emptyList();
        List<SearchHistory> list = searchHistoryRepository.findByUserOrderByCreatedAtDesc(user);
        if (list.size() > 10) return list.subList(0, 10);
        return list;
    }

    // Removed @Transactional: run search without opening a Spring transaction to avoid rollback-only issues
    public Map<String, List<?>> searchProductsAndShops(String q, int maxResults) {
        Map<String, List<?>> res = new HashMap<>();
        if (q == null || q.trim().isEmpty()) {
            res.put("products", Collections.emptyList());
            res.put("shops", Collections.emptyList());
            return res;
        }
        String pattern = "%" + q.trim().toLowerCase() + "%";

        List<Product> products = Collections.emptyList();
        List<ShopInfo> shops = Collections.emptyList();

        // Defensive: any error must not bubble and mark a transaction rollback-only
        try {
            products = tryProductSearch(pattern, maxResults);
        } catch (Throwable ex) {
            // log and continue with empty products
            log.error("Product search failed (safe fallback to empty list). Query: '{}'", q, ex);
            products = Collections.emptyList();
        }

        try {
            TypedQuery<ShopInfo> sq = em.createQuery(
                    "SELECT s FROM ShopInfo s WHERE LOWER(s.shopName) LIKE :pat AND (s.isDelete = false OR s.isDelete IS NULL)",
                    ShopInfo.class);
            sq.setParameter("pat", pattern);
            sq.setMaxResults(maxResults);
            shops = sq.getResultList();
        } catch (Throwable ex) {
            log.error("Shop search failed (safe fallback to empty list). Query: '{}'", q, ex);
            shops = Collections.emptyList();
        }

        res.put("products", products);
        res.put("shops", shops);
        return res;
    }

    // Helper: try multiple candidate field names for product title/name and description
    private List<Product> tryProductSearch(String pattern, int maxResults) {
        List<String> titleCandidates = Arrays.asList("title", "name", "productName", "product_title", "productNameVn");
        List<String> descCandidates = Arrays.asList("description", "shortDescription", "detail", "content", "desc");

        // use JPA metamodel to check attribute presence before executing JPQL
        for (String t : titleCandidates) {
            for (String d : descCandidates) {
                boolean hasT = hasAttributeOnProduct(t);
                boolean hasD = hasAttributeOnProduct(d);
                if (!hasT && !hasD) {
                    continue; // skip if neither attribute exists
                }

                // build JPQL with only attributes that exist to avoid referencing non-existent paths
                StringBuilder where = new StringBuilder("SELECT p FROM Product p WHERE (");
                boolean firstClause = true;
                if (hasT) {
                    where.append("LOWER(p.").append(t).append(") LIKE :pat");
                    firstClause = false;
                }
                if (hasD) {
                    if (!firstClause) where.append(" OR ");
                    where.append("LOWER(p.").append(d).append(") LIKE :pat");
                }
                where.append(") AND (p.isDelete = false OR p.isDelete IS NULL)");

                String jpql = where.toString();
                try {
                    TypedQuery<Product> pq = em.createQuery(jpql, Product.class);
                    pq.setParameter("pat", pattern);
                    pq.setMaxResults(maxResults);
                    return pq.getResultList();
                } catch (Throwable ex) {
                    // If execution still fails (very defensive), try next combination
                    log.debug("Product JPQL execution failed for attributes [{} , {}]: {}", t, d, ex.toString());
                }
            }
        }
        // No compatible field combination found or all queries returned nothing => safe empty list
        return Collections.emptyList();
    }

    // Null-safe check using JPA metamodel to ensure Product has that attribute
    private boolean hasAttributeOnProduct(String attrName) {
        if (attrName == null || attrName.isBlank()) return false;
        try {
            // defensive: getMetamodel may throw; catch Throwable at caller too
            em.getMetamodel().entity(Product.class).getAttribute(attrName);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        } catch (Throwable ex) {
            log.debug("Metamodel attribute check failed for '{}': {}", attrName, ex.toString());
            return false;
        }
    }
}
