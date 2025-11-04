package com.mmo.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mmo.dto.ChangePasswordRequest;
import com.mmo.dto.UpdateProfileRequest;
import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import com.mmo.service.AccountService;

@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User updateProfile(String email, UpdateProfileRequest request) {
        // Find user by email
        User user = userRepository.findByEmailAndIsDelete(email, false);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        // Validate and update full name
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            String fullName = request.getFullName().trim();
            if (fullName.length() < 2) {
                throw new IllegalArgumentException("Full name must be at least 2 characters");
            }
            if (fullName.length() > 255) {
                throw new IllegalArgumentException("Full name is too long (max 255 characters)");
            }
            user.setFullName(fullName);
        } else {
            throw new IllegalArgumentException("Full name is required");
        }

        // Validate and update phone
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String phone = request.getPhone().trim();
            // Phone validation: exactly 10 digits starting with 0
            if (!phone.matches("^0\\d{9}$")) {
                throw new IllegalArgumentException("Phone number must be exactly 10 digits and start with 0");
            }
            user.setPhone(phone);
        } else {
            // Allow clearing phone
            user.setPhone(null);
        }

        // Save and return
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        // Find user by email
        User user = userRepository.findByEmailAndIsDelete(email, false);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        // Validate request
        if (request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
            throw new IllegalArgumentException("Current password is required");
        }
        if (request.getNewPassword() == null || request.getNewPassword().isEmpty()) {
            throw new IllegalArgumentException("New password is required");
        }
        if (request.getConfirmPassword() == null || request.getConfirmPassword().isEmpty()) {
            throw new IllegalArgumentException("Password confirmation is required");
        }

        // Check if new password matches confirmation
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }

        // Validate new password strength (same rules as registration)
        if (request.getNewPassword().length() < 8 ||
                !request.getNewPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            throw new IllegalArgumentException("New password must be at least 8 characters and include letters and numbers");
        }

        // For OAuth users (Google login), password might be null
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            // Verify current password
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
        } else {
            // OAuth user trying to set password for the first time - allow it without current password
            // This is acceptable as they're already authenticated via OAuth
        }

        // Check if new password is different from current (if current exists)
        if (user.getPassword() != null &&
                passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
