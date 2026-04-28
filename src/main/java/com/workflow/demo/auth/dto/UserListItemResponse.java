package com.workflow.demo.auth.dto;

import com.workflow.demo.domain.entity.User;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserListItemResponse {
    String id;
    String nombre;
    String email;
    String departmentId;

    public static UserListItemResponse from(User user) {
        return UserListItemResponse.builder()
            .id(user.getId() != null ? user.getId().toHexString() : null)
            .nombre(user.getNombre())
            .email(user.getEmail())
            .departmentId(user.getDepartmentId() != null ? user.getDepartmentId().toHexString() : null)
            .build();
    }
}
