package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class SellerBankInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private String bankName;
    @Column(nullable = false)
    private String accountNumber;
    private String branch;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
    private Long createdBy;
    private Long deletedBy;
    private boolean isDelete;
}