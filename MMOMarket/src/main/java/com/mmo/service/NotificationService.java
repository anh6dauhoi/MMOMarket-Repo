package com.mmo.service;

import com.mmo.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    Page<Notification> getNotificationsForUser(String email, String status, String search, Pageable pageable);

    void createNotificationForUser(Long userId, String title, String content);

    void createNotificationForRole(String role, String title, String content);
}
