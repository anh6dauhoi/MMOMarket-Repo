package com.mmo.mq.dto;

import java.io.Serializable;

public record WithdrawalMessage(Long withdrawalId, Long sellerId, Long amount) implements Serializable {}

