package com.mmo.repository;

import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Đảm bảo tên biến là isDelete (camelCase) để ánh xạ đúng với DB
    User findByEmailAndIsDelete(String email, boolean isDelete);

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndIsDeleteFalse(String email);
    Optional<User> findByIdAndIsDeleteFalse(Long id);
    List<User> findByIsDeleteFalse();
    List<User> findByRoleAndIsDeleteFalse(String role);
    List<User> findByRoleContainingAndIsDeleteFalse(String role);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIsDeleteFalse(String email);
    List<User> findByShopStatusAndIsDeleteFalse(String shopStatus);
    long countByRoleContainingAndIsDeleteFalse(String role);

    User findByDepositCodeAndIsDelete(String depositCode, boolean isDelete);

    @Modifying
    @Query("UPDATE User u SET u.coins = COALESCE(u.coins,0) + :delta WHERE u.id = :id")
    int addCoins(@Param("id") Long id, @Param("delta") Long delta);

    // New: find users by role (case-insensitive) and not deleted — used to notify all admins
    List<User> findByRoleIgnoreCaseAndIsDelete(String role, boolean isDelete);
}