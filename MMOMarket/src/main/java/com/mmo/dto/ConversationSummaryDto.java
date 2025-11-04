package com.mmo.dto;

import java.util.Date;

public class ConversationSummaryDto {
    private Long partnerId;
    private String partnerName;
    private String lastMessage;
    private Date lastMessageTime;
    private boolean isSentByMe;
    private boolean admin; // New: mark if this conversation is with Admin (Support)

    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public Date getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(Date lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public boolean isSentByMe() { return isSentByMe; }
    public void setSentByMe(boolean sentByMe) { isSentByMe = sentByMe; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}
