package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Withdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;
    @ManyToOne
    @JoinColumn(name = "bank_info_id", nullable = false)
    private SellerBankInfo bankInfo;
    @Column(nullable = false)
    private Long amount;
    private String status;
    private String bankName;
    private String accountNumber;
    private String branch;
    private String proofFile;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
    private Long createdBy;
    private Long deletedBy;
    private boolean isDelete;
}