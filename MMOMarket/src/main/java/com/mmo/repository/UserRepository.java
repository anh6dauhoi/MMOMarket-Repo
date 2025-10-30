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

    // New: find users by role (case-insensitive) and not deleted — used to notify all admins
    List<User> findByRoleIgnoreCaseAndIsDelete(String role, boolean isDelete);
}