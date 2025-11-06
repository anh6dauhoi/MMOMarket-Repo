package com.mmo.dto;

import com.mmo.entity.Complaint;

import java.util.Date;

public class SellerComplaintListItem {
    private final Long id;
    private final Long transactionId;
    private final Complaint.ComplaintType complaintType;
    private final Complaint.ComplaintStatus status;
    private final Date createdAt;
    private final Date updatedAt;
    private final Date respondedAt;
    private final Long customerId;
    private final String customerName;
    private final String description;

    public SellerComplaintListItem(Long id,
                                   Long transactionId,
                                   Complaint.ComplaintType complaintType,
                                   Complaint.ComplaintStatus status,
                                   Date createdAt,
                                   Date updatedAt,
                                   Date respondedAt,
                                   Long customerId,
                                   String customerName,
                                   String description) {
        this.id = id;
        this.transactionId = transactionId;
        this.complaintType = complaintType;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.respondedAt = respondedAt;
        this.customerId = customerId;
        this.customerName = customerName;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getTransactionId() { return transactionId; }
    public Complaint.ComplaintType getComplaintType() { return complaintType; }
    public Complaint.ComplaintStatus getStatus() { return status; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public Date getRespondedAt() { return respondedAt; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getDescription() { return description; }
}

