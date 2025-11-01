package com.mmo.mq.dto;

import java.io.Serializable;

/**
 * Message to request creating a new Withdrawal from a seller action.
 * This is processed by a queue consumer to avoid race conditions (e.g. two tabs submitting simultaneously).
 */
public record WithdrawalCreateMessage(
        Long sellerId,
        Long bankInfoId,
        Long amount,
        String bankName,
        String accountNumber,
        String accountName,
        String branch,
        String otp,
        String dedupeKey
) implements Serializable {}
