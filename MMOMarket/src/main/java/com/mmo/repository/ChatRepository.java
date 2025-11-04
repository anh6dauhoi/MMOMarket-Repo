package com.mmo.repository;

import com.mmo.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat, Long> {
    // ...existing code...
}

