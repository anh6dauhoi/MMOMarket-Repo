package com.mmo.repository;

import com.mmo.entity.Product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p JOIN p.category c JOIN ProductVariant pv ON p.id = pv.product.id " +
            "WHERE p.isDelete = false AND pv.isDelete = false AND pv.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findAllProducts(@Param("minPrice") Long minPrice, @Param("maxPrice") Long maxPrice, Pageable pageable);

    // Order by total units sold (COUNT completed transactions), not coin amount
    @Query("SELECT p FROM Product p " +
           "LEFT JOIN Transaction t ON p.id = t.product.id AND t.isDelete = false AND LOWER(t.status) = 'completed' " +
           "WHERE p.isDelete = false " +
           "GROUP BY p.id " +
           "ORDER BY COUNT(t.id) DESC")
    List<Product> findTopSellingProducts(Pageable pageable);

    // Total units sold for a product: count of completed transactions
    @Query("SELECT COALESCE(COUNT(t.id), 0) FROM Transaction t " +
           "WHERE t.product.id = :productId AND t.isDelete = false AND LOWER(t.status) = 'completed'")
    Long getTotalSoldForProduct(@Param("productId") Long productId);

    // Total units sold for a seller: count of completed transactions across all products
    @Query("SELECT COALESCE(COUNT(t.id), 0) FROM Transaction t JOIN t.product p " +
           "WHERE p.seller.id = :sellerId AND t.isDelete = false AND LOWER(t.status) = 'completed'")
    Long getTotalSoldForSeller(@Param("sellerId") Long sellerId);

    @Query("SELECT p FROM Product p JOIN p.category c JOIN ProductVariant pv ON p.id = pv.product.id " +
            "WHERE c.id = :categoryId AND p.isDelete = false AND pv.isDelete = false " +
            "AND pv.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId,
                                   @Param("minPrice") Long minPrice,
                                   @Param("maxPrice") Long maxPrice,
                                   Pageable pageable);

    @Query("SELECT p FROM Product p JOIN ProductVariant pv ON p.id = pv.product.id " +
            "WHERE p.seller.id = :sellerId AND p.isDelete = false AND pv.isDelete = false")
    List<Product> findBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT DISTINCT p FROM Product p " +
            "JOIN ProductVariant pv ON p.id = pv.product.id " +
            "WHERE p.seller.id = :sellerId AND p.isDelete = false AND pv.isDelete = false " +
            "AND pv.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findBySellerWithPrice(@Param("sellerId") Long sellerId,
                                        @Param("minPrice") Long minPrice,
                                        @Param("maxPrice") Long maxPrice,
                                        Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p " +
            "JOIN p.category c " +
            "JOIN ProductVariant pv ON p.id = pv.product.id " +
            "WHERE p.seller.id = :sellerId AND c.id = :categoryId " +
            "AND p.isDelete = false AND pv.isDelete = false " +
            "AND pv.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findBySellerAndCategoryWithPrice(@Param("sellerId") Long sellerId,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("minPrice") Long minPrice,
                                                   @Param("maxPrice") Long maxPrice,
                                                   Pageable pageable);
}
