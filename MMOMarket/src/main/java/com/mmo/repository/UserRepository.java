package com.mmo.repository;

import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}