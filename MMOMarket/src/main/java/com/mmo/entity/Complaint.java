package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;
    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;
    @Column(nullable = false)
    private String description;
    private String evidence;
    private String status;
    private String resolution;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
    private Long createdBy;
    private Long deletedBy;
    private boolean isDelete;
}