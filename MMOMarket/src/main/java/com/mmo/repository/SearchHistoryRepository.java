package com.mmo.repository;

import com.mmo.entity.SearchHistory;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    List<SearchHistory> findByUserOrderByCreatedAtDesc(User user);

    // new: find existing identical query for a user
    Optional<SearchHistory> findByUserAndSearchQuery(User user, String searchQuery);
}
