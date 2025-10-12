package com.mmo.service;

import com.mmo.entity.Notification;
import com.mmo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    Page<Notification> getNotificationsForUser(String email, String status, Pageable pageable);
    void createNotificationForUser(User user, String title, String content);
}
