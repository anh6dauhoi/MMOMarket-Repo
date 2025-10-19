package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "BlogComments", indexes = {
        @Index(name = "idx_blog_id", columnList = "blog_id"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_parent_comment_id", columnList = "parent_comment_id")
})
public class BlogComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // blog_id FK -> Blogs(id)
    @ManyToOne
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    // user_id FK -> Users(id)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // content TEXT NOT NULL
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // parent_comment_id self reference
    @ManyToOne
    @JoinColumn(name = "parent_comment_id")
    private BlogComment parentComment;

    // Optional reverse mapping to children (no cascade remove needed; DB handles ON DELETE SET NULL)
    @OneToMany(mappedBy = "parentComment", fetch = FetchType.LAZY)
    private Set<BlogComment> replies;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private Date updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "isDelete", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDelete;

    // Readonly audit navigations (optional)
    @ManyToOne
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;
}