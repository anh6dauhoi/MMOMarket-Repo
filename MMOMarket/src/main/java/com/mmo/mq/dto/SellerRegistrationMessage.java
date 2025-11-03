package com.mmo.mq.dto;

import java.io.Serializable;

/**
 * Message to request activating a seller account.
 * Processed by a queue consumer to avoid race conditions (multiple submissions, multi-tab).
 */
public record SellerRegistrationMessage(
        Long userId,
        String shopName,
        String description,
        String dedupeKey
) implements Serializable {}

