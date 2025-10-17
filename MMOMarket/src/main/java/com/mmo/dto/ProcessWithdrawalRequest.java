package com.mmo.dto;

public class ProcessWithdrawalRequest {
    private String status;     // "Approved" or "Rejected"
    private String proofFile;  // required if Approved
    private String reason;     // required if Rejected

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProofFile() {
        return proofFile;
    }

    public void setProofFile(String proofFile) {
        this.proofFile = proofFile;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
