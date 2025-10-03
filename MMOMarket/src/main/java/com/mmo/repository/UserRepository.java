package com.mmo.repository;

import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // Đảm bảo tên biến là isDelete (camelCase) để ánh xạ đúng với DB
    User findByEmailAndIsDelete(String email, boolean isDelete);
}