package com.mmo.service;

import com.mmo.entity.Notification;
import com.mmo.entity.User;
import com.mmo.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Override
    public Page<Notification> getNotificationsForUser(String email, String status, Pageable pageable) {
        if (StringUtils.hasText(status)) {
            return notificationRepository.findByUser_EmailAndStatusOrderByCreatedAtDesc(email, status, pageable);
        } else {
            return notificationRepository.findByUser_EmailOrderByCreatedAtDesc(email, pageable);
        }
    }

    @Override
    public void createNotificationForUser(User user, String title, String content) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setContent(content);
        n.setStatus("Unread");
        n.setCreatedAt(new Date());
        n.setUpdatedAt(new Date());
        notificationRepository.save(n);
    }
}
