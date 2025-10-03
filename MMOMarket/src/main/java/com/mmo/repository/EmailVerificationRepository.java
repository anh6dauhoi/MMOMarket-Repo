package com.mmo.repository;

import com.mmo.entity.EmailVerification;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    // Đảm bảo ánh xạ đúng với entity và DB
    EmailVerification findByUserAndVerificationCode(User user, String code);
}