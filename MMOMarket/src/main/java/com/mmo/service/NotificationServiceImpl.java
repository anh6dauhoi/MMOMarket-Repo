package com.mmo.service;

import com.mmo.entity.Notification;
import com.mmo.entity.User;
import com.mmo.repository.NotificationRepository;
import com.mmo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Page<Notification> getNotificationsForUser(String email, String status, String search, Pageable pageable) {
        if (StringUtils.hasText(search)) {
            if (StringUtils.hasText(status)) {
                return notificationRepository.findByUser_EmailAndStatusAndTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(email, status, search, pageable);
            } else {
                return notificationRepository.findByUser_EmailAndTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(email, search, pageable);
            }
        } else {
            if (StringUtils.hasText(status)) {
                return notificationRepository.findByUser_EmailAndStatusOrderByCreatedAtDesc(email, status, pageable);
            } else {
                return notificationRepository.findByUser_EmailOrderByCreatedAtDesc(email, pageable);
            }
        }
    }

    @Override
    @Async("notificationExecutor")
    public void createNotificationForUser(Long userId, String title, String content) {
        User user = null;
        try {
            user = userRepository.findById(userId).orElse(null);
        } catch (Exception ignored) {}
        if (user == null) {
            // can't create notification without user reference; bail silently
            return;
        }
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setContent(content);
        n.setStatus("Unread");
        n.setCreatedAt(new Date());
        n.setUpdatedAt(new Date());
        notificationRepository.save(n);
    }

    @Override
    @Async("notificationExecutor")
    public void createNotificationForRole(String role, String title, String content) {
        try {
            List<User> users = userRepository.findByRoleIgnoreCaseAndIsDelete(role, false);
            if (users == null || users.isEmpty()) return;
            Date now = new Date();
            for (User user : users) {
                try {
                    Notification n = new Notification();
                    n.setUser(user);
                    n.setTitle(title);
                    n.setContent(content);
                    n.setStatus("Unread");
                    n.setCreatedAt(now);
                    n.setUpdatedAt(now);
                    notificationRepository.save(n);
                } catch (Exception ignored) {
                    // continue creating for other users
                }
            }
        } catch (Exception ignored) {
            // best-effort: swallow exceptions to not break caller flow
        }
    }
}
