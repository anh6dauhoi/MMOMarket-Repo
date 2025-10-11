package com.mmo.repository;

import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Đảm bảo tên biến là isDelete (camelCase) để ánh xạ đúng với DB
    User findByEmailAndIsDelete(String email, boolean isDelete);
    User findByDepositCodeAndIsDelete(String depositCode, boolean isDelete);

    Optional<User> findByEmail(String email);
}