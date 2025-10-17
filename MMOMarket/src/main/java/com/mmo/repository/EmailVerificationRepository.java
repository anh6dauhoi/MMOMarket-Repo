package com.mmo.repository;

import com.mmo.entity.EmailVerification;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    EmailVerification findByUserAndVerificationCode(User user, String code);

    Optional<EmailVerification> findTopByUserOrderByCreatedAtDesc(User user);

    Optional<EmailVerification> findByUserAndVerificationCodeAndIsUsedFalse(User user, String verificationCode);
}