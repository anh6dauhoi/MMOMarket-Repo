package com.mmo.repository;

import com.mmo.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// ADD: imports for JPQL updates
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop20ByUser_IdAndIsDeleteOrderByCreatedAtDesc(Long userId, boolean isDelete);
    List<Notification> findTop20ByUser_EmailAndIsDeleteOrderByCreatedAtDesc(String email, boolean isDelete);
    List<Notification> findTop20ByUser_IdOrderByCreatedAtDesc(Long userId);
    List<Notification> findTop20ByUser_EmailOrderByCreatedAtDesc(String email);

    long countByUser_IdAndIsDelete(Long userId, boolean isDelete);
    long countByUser_EmailAndIsDelete(String email, boolean isDelete);

    Page<Notification> findByUser_EmailOrderByCreatedAtDesc(String email, Pageable pageable);

    List<Notification> findTop20ByUser_IdAndIsDeleteAndStatusOrderByCreatedAtDesc(Long userId, boolean isDelete, String status);
    long countByUser_IdAndIsDeleteAndStatus(Long userId, boolean isDelete, String status);

    // Added: exact names used in GlobalModelAttributes
    List<Notification> findTop20ByUser_IdAndStatusAndIsDeleteOrderByCreatedAtDesc(Long userId, String status, boolean isDelete);
    List<Notification> findTop20ByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    List<Notification> findTop20ByUser_EmailAndStatusAndIsDeleteOrderByCreatedAtDesc(String email, String status, boolean isDelete);
    List<Notification> findTop20ByUser_EmailAndStatusOrderByCreatedAtDesc(String email, String status);

    long countByUser_IdAndStatusAndIsDelete(Long userId, String status, boolean isDelete);
    long countByUser_IdAndStatus(Long userId, String status);
    long countByUser_EmailAndStatusAndIsDelete(String email, String status, boolean isDelete);
    long countByUser_EmailAndStatus(String email, String status);

    // Optional pageable variants
    List<Notification> findByUser_EmailAndStatusOrderByCreatedAtDesc(String email, String status);
    Page<Notification> findByUser_EmailAndStatusOrderByCreatedAtDesc(String email, String status, Pageable pageable);

    // BULK UPDATES (used by NotificationController)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :toStatus where n.user.id = :userId and n.status = :fromStatus")
    int updateStatusForUserId(@Param("userId") Long userId,
                              @Param("fromStatus") String fromStatus,
                              @Param("toStatus") String toStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :toStatus where lower(n.user.email) = lower(:email) and n.status = :fromStatus")
    int updateStatusForUserEmail(@Param("email") String email,
                                 @Param("fromStatus") String fromStatus,
                                 @Param("toStatus") String toStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :toStatus " +
           "where n.id in (:ids) and lower(n.user.email) = lower(:email) and n.status = :fromStatus")
    int updateStatusForIdsAndEmail(@Param("email") String email,
                                   @Param("ids") List<Long> ids,
                                   @Param("fromStatus") String fromStatus,
                                   @Param("toStatus") String toStatus);
}
