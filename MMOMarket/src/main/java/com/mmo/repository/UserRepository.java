package com.mmo.repository;

import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmailAndIsDelete(String email, boolean isDelete);

    User findByDepositCodeAndIsDelete(String depositCode, boolean isDelete);

    Optional<User> findByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.coins = COALESCE(u.coins,0) + :delta WHERE u.id = :id")
    int addCoins(@Param("id") Long id, @Param("delta") Long delta);

    // Atomic deduction with guard to prevent negative balance; returns number of rows updated (0 if insufficient)
    @Modifying
    @Query("UPDATE User u SET u.coins = COALESCE(u.coins,0) - :amount WHERE u.id = :id AND COALESCE(u.coins,0) >= :amount")
    int deductCoinsIfEnough(@Param("id") Long id, @Param("amount") Long amount);

    // Atomic: deduct registration fee and activate shop status only if not already Active and enough coins
    @Modifying
    @Query("UPDATE User u SET u.coins = COALESCE(u.coins,0) - :amount, u.shopStatus = 'Active' " +
            "WHERE u.id = :id AND (u.shopStatus IS NULL OR LOWER(u.shopStatus) <> 'active') AND COALESCE(u.coins,0) >= :amount")
    int activateSellerAndDeductIfNotActive(@Param("id") Long id, @Param("amount") Long amount);

    // New: find users by role (case-insensitive) and not deleted — used to notify all admins
    List<User> findByRoleIgnoreCaseAndIsDelete(String role, boolean isDelete);

    List<User> findByRoleAndShopStatus(String role, String shopStatus);
    @Query(
            value = "SELECT u.id AS userId, u.full_name AS fullName, " +
                    "COUNT(t.id) AS totalProductsSold, " +
                    "COALESCE(AVG(r.rating), 0) AS averageRating " +
                    "FROM Users u " +
                    "JOIN Transactions t ON u.id = t.seller_id " +
                    "LEFT JOIN Products p ON u.id = p.seller_id " +
                    "LEFT JOIN Reviews r ON p.id = r.product_id " +
                    "WHERE u.role = 'customer' AND u.shop_status = 'active' AND u.isDelete = 0 " +
                    "GROUP BY u.id, u.full_name " +
                    "ORDER BY totalProductsSold DESC, averageRating DESC",
            nativeQuery = true
    )
    List<Object[]> findReputableSellers();

    Optional<User> findByEmailAndIsDeleteFalse(String email);
    Optional<User> findByIdAndIsDeleteFalse(Long id);
    List<User> findByIsDeleteFalse();
    List<User> findByRoleAndIsDeleteFalse(String role);
    List<User> findByRoleContainingAndIsDeleteFalse(String role);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIsDeleteFalse(String email);
    List<User> findByShopStatusAndIsDeleteFalse(String shopStatus);
    long countByRoleContainingAndIsDeleteFalse(String role);

}