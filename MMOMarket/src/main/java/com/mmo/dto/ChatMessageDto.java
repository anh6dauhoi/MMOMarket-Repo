package com.mmo.dto;

import com.mmo.entity.Chat;

import java.util.Date;

public class ChatMessageDto {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String message;
    private Date createdAt;
    private String filePath;
    private String fileType;
    private String fileName;
    private Long fileSize;

    public ChatMessageDto() {}

    public ChatMessageDto(Chat c) {
        this.id = c.getId();
        this.senderId = c.getSender() != null ? c.getSender().getId() : null;
        this.receiverId = c.getReceiver() != null ? c.getReceiver().getId() : null;
        this.message = c.getMessage();
        this.createdAt = c.getCreatedAt();
        this.filePath = c.getFilePath();
        this.fileType = c.getFileType();
        this.fileName = c.getFileName();
        this.fileSize = c.getFileSize();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
}
