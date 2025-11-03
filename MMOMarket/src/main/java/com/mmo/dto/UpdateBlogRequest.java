package com.mmo.dto;

import lombok.Data;

@Data
public class UpdateBlogRequest {
    private String title;
    private String content;
    private String image;
}
