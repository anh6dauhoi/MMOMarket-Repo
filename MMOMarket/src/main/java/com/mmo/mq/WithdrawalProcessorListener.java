package com.mmo.mq;

// Deprecated: This listener handled post-approval payout via queue.
// Requirement updated: queue is only for seller request creation; admin approval is synchronous.
// Keeping the class (without Spring annotations) for reference; it is no longer a Spring bean.

import com.mmo.entity.Withdrawal;
import com.mmo.mq.dto.WithdrawalMessage;
import com.mmo.repository.WithdrawalRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class WithdrawalProcessorListener {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalProcessorListener.class);

    private final WithdrawalRepository withdrawalRepository;

    public WithdrawalProcessorListener(WithdrawalRepository withdrawalRepository) {
        this.withdrawalRepository = withdrawalRepository;
    }

    @Transactional
    public void handle(WithdrawalMessage msg) {
        // No-op: listener disabled per new requirements
        log.info("WithdrawalProcessorListener is disabled; ignoring message {}", msg);
    }
}
