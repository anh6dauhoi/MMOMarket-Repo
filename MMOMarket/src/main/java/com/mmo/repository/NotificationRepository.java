package com.mmo.repository;

import com.mmo.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Fetch latest UNREAD notifications
    List<Notification> findTop20ByUser_IdAndStatusAndIsDeleteOrderByCreatedAtDesc(Long userId, String status, boolean isDelete);
    List<Notification> findTop20ByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    List<Notification> findTop20ByUser_EmailAndStatusAndIsDeleteOrderByCreatedAtDesc(String email, String status, boolean isDelete);
    List<Notification> findTop20ByUser_EmailAndStatusOrderByCreatedAtDesc(String email, String status);

    // Count unread
    long countByUser_IdAndStatusAndIsDelete(Long userId, String status, boolean isDelete);
    long countByUser_IdAndStatus(Long userId, String status);
    long countByUser_EmailAndStatusAndIsDelete(String email, String status, boolean isDelete);
    long countByUser_EmailAndStatus(String email, String status);

    // Mark all unread as readed (by userId/email)
    @Modifying
    @Query("update Notification n set n.status = :to where n.user.id = :userId and n.status = :from and n.isDelete = false")
    int updateStatusForUserId(@Param("userId") Long userId, @Param("from") String from, @Param("to") String to);

    @Modifying
    @Query("update Notification n set n.status = :to where n.user.email = :email and n.status = :from and n.isDelete = false")
    int updateStatusForUserEmail(@Param("email") String email, @Param("from") String from, @Param("to") String to);

    @Modifying
    @Query("update Notification n set n.status = :to where n.user.email = :email and n.id in :ids and n.status = :from and n.isDelete = false")
    int updateStatusForIdsAndEmail(@Param("email") String email,
                                   @Param("ids") List<Long> ids,
                                   @Param("from") String from,
                                   @Param("to") String to);

    Page<Notification> findByUser_EmailAndStatusOrderByCreatedAtDesc(String email, String status, Pageable pageable);
    Page<Notification> findByUser_EmailOrderByCreatedAtDesc(String email, Pageable pageable);
}
