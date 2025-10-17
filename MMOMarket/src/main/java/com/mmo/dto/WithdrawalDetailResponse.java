package com.mmo.dto;

public record WithdrawalDetailResponse(String sellerName, String sellerEmail, Long amount, String bankName,
                                       String accountNumber, String branch, String status, String proofFile,
                                       String vietQrUrl, String createdAt) {
}
