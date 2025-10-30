package com.mmo.dto;

public class CreateWithdrawalRequest {
    private Long amount;
    private Long bankInfoId;
    // Optional display fields (submitted from the form as hidden inputs)
    private String bankName;
    private String accountNumber;
    private String accountHolder;
    private String branch;
    // OTP code for verifying withdrawal
    private String otp;

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getBankInfoId() {
        return bankInfoId;
    }

    public void setBankInfoId(Long bankInfoId) {
        this.bankInfoId = bankInfoId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public void setAccountHolder(String accountHolder) {
        this.accountHolder = accountHolder;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
