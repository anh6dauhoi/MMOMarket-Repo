package com.mmo.service;

import com.mmo.dto.ChangePasswordRequest;
import com.mmo.dto.UpdateProfileRequest;
import com.mmo.entity.User;

public interface AccountService {
    /**
     * Update user profile information (fullName, phone)
     * @param email User's email
     * @param request Profile update request
     * @return Updated user
     */
    User updateProfile(String email, UpdateProfileRequest request);

    /**
     * Change user password
     * @param email User's email
     * @param request Password change request
     */
    void changePassword(String email, ChangePasswordRequest request);
}
