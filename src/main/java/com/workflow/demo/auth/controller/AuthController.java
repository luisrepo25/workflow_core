package com.workflow.demo.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workflow.demo.auth.dto.AuthResponse;
import com.workflow.demo.auth.dto.ChangePasswordRequest;
import com.workflow.demo.auth.dto.LoginRequest;
import com.workflow.demo.auth.dto.RefreshTokenRequest;
import com.workflow.demo.auth.dto.RegisterRequest;
import com.workflow.demo.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error en login: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error en registro: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al refrescar token: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            String userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
            authService.changePassword(userId, request);
            return ResponseEntity.ok("Contraseña cambiada exitosamente");
        } catch (Exception e) {
            log.error("Error al cambiar contraseña: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        try {
            String userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
            authService.logout(userId);
            return ResponseEntity.ok("Sesión cerrada exitosamente");
        } catch (Exception e) {
            log.error("Error al cerrar sesión: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
