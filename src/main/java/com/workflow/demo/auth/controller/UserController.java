package com.workflow.demo.auth.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.workflow.demo.auth.dto.UserListItemResponse;
import com.workflow.demo.auth.service.UserQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT','FUNCIONARIO','DESIGNER','ADMIN')")
    public List<UserListItemResponse> listUsers(
        @RequestParam(required = false) String role,
        @RequestParam(required = false) Boolean activo
    ) {
        return userQueryService.listUsers(role, activo);
    }
}
