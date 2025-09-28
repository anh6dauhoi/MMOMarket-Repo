package com.mmo.repository;

import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmailAndIsDelete(String email, boolean isDelete);
}