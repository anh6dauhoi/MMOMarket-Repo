package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String email;
    private String password;
    private String fullName;
    @Column(columnDefinition = "JSON NOT NULL")
    private String role;
    private String phone;
    private String shopStatus;
    private Long coins;
    @Column(columnDefinition = "JSON")
    private String permissions;
    private boolean isVerified;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
    private Long createdBy;
    private Long deletedBy;
    private boolean isDelete;
}