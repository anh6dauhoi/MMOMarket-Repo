package com.mmo.service;

import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<User> findAll() {
        return userRepository.findByIsDeleteFalse();
    }

    public User findById(Long id) {
        return userRepository.findByIdAndIsDeleteFalse(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmailAndIsDeleteFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    public List<User> findByRole(String role) {
        return userRepository.findByRoleContainingAndIsDeleteFalse(role);
    }

    public List<User> findByShopStatus(String shopStatus) {
        return userRepository.findByShopStatusAndIsDeleteFalse(shopStatus);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public User register(User user) {
        // Kiểm tra email đã tồn tại
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Mã hóa mật khẩu
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // Set default values
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("ROLE_CUSTOMER");
        }
        user.setCoins(0L);
        user.setVerified(false);
        user.setShopStatus("Inactive");
        user.setDelete(false);

        return userRepository.save(user);
    }

    public User update(Long id, User userDetails) {
        User user = findById(id);

        if (userDetails.getFullName() != null) {
            user.setFullName(userDetails.getFullName());
        }
        if (userDetails.getPhone() != null) {
            user.setPhone(userDetails.getPhone());
        }
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }

        user.setUpdatedAt(new Date());
        return userRepository.save(user);
    }

    public void updateCoins(Long userId, Long coins) {
        User user = findById(userId);
        user.setCoins(coins);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    public void addCoins(Long userId, Long amount) {
        User user = findById(userId);
        user.setCoins(user.getCoins() + amount);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    public void deductCoins(Long userId, Long amount) {
        User user = findById(userId);
        if (user.getCoins() < amount) {
            throw new RuntimeException("Insufficient coins");
        }
        user.setCoins(user.getCoins() - amount);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    public void updateShopStatus(Long userId, String status) {
        User user = findById(userId);
        user.setShopStatus(status);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    public void updateRole(Long userId, String role) {
        User user = findById(userId);
        user.setRole(role);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    public void verifyEmail(Long userId) {
        User user = findById(userId);
        user.setVerified(true);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    public void softDelete(Long id, Long deletedBy) {
        User user = findById(id);
        user.setDelete(true);
        user.setDeletedBy(deletedBy);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailAndIsDeleteFalse(email);
    }

    public long countSellers() {
        return userRepository.countByRoleContainingAndIsDeleteFalse("SELLER");
    }

    public long countCustomers() {
        return userRepository.countByRoleContainingAndIsDeleteFalse("CUSTOMER");
    }
}