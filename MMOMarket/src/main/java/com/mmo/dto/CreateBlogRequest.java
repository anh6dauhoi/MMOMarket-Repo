package com.mmo.dto;

import lombok.Data;

@Data
public class CreateBlogRequest {
    private String title;
    private String content;
    private String image;
}
