package com.mmo.dto;

import java.util.Date;

import com.mmo.entity.Category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private String type;
    private Date createdAt;
    private Date updatedAt;
    private Long createdBy;
    private String createdByName;
    private boolean isDelete;

    public static CategoryResponse fromEntity(Category category) {
        CategoryResponseBuilder builder = CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .type(category.getType())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(category.getCreatedBy())
                .isDelete(category.isDelete());

        if (category.getCreatedByUser() != null) {
            builder.createdByName(category.getCreatedByUser().getFullName());
        }

        return builder.build();
    }
}
