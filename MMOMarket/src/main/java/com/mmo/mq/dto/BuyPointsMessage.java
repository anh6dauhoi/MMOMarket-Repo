package com.mmo.mq.dto;

public record BuyPointsMessage(
        Long userId,
        Long pointsToBuy,
        Long costCoins,
        String otp,
        String dedupeKey
) {}

