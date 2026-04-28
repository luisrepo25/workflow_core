package com.workflow.demo.auth.service;

import java.time.Instant;
import java.util.ArrayList;

import org.bson.types.ObjectId;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.workflow.demo.auth.dto.AuthResponse;
import com.workflow.demo.auth.dto.ChangePasswordRequest;
import com.workflow.demo.auth.dto.LoginRequest;
import com.workflow.demo.auth.dto.RegisterRequest;
import com.workflow.demo.auth.security.JwtTokenProvider;
import com.workflow.demo.domain.embedded.PushToken;
import com.workflow.demo.domain.entity.Role;
import com.workflow.demo.domain.entity.User;
import com.workflow.demo.domain.enums.PushPlatform;
import com.workflow.demo.repository.RoleRepository;
import com.workflow.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + request.getEmail()));

        if (!user.isActivo()) {
            throw new RuntimeException("Usuario inactivo");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        // 📱 Procesar y guardar FCM token si se proporciona
        String normalizedFcmToken = request.getFcmToken() != null ? request.getFcmToken().trim() : null;
        if (normalizedFcmToken != null && !normalizedFcmToken.isBlank()) {
            user.setFcmToken(normalizedFcmToken);

            if (user.getPushTokens() == null) {
                user.setPushTokens(new ArrayList<>());
            }

            PushToken pushToken = new PushToken();
            pushToken.setToken(normalizedFcmToken);
            pushToken.setPlatform(PushPlatform.android); // Default: android
            pushToken.setLastUsedAt(Instant.now());
            pushToken.setActivo(true);
            
            // Remover tokens duplicados del mismo token si ya existen
            user.getPushTokens().removeIf(p -> normalizedFcmToken.equals(p.getToken()));
            // Agregar el nuevo token
            user.getPushTokens().add(pushToken);
            
            user = userRepository.save(user);
            log.info("✅ FCM token guardado para usuario: {}", user.getEmail());
        } else {
            log.debug("Login sin fcm_token para usuario: {}", user.getEmail());
        }

        String accessToken = jwtTokenProvider.generateAccessToken(
            user.getId().toString(),
            user.getEmail(),
            getRoleName(user.getRoleId())
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
            user.getId().toString(),
            user.getEmail()
        );

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Usuario ya existe con ese email");
        }

        // ✅ Validar que el rol sea válido
        String roleName;
        try {
            roleName = com.workflow.demo.domain.enums.RoleEnum.fromDisplayName(request.getRole()).getDisplayName();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Rol no válido. Use: Diseñador, Funcionario, Cliente, Admin");
        }

        // ✅ Obtener el rol solicitado (no "USER" por defecto)
        Role role = roleRepository.findByNombre(roleName)
            .orElseThrow(() -> new RuntimeException("El rol " + roleName + " no existe en el sistema. Contacte al administrador."));

        User user = new User();
        user.setNombre(request.getNombre());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setTelefono(request.getTelefono());
        user.setRoleId(role.getId());
        
        if (request.getDepartmentId() != null && !request.getDepartmentId().isEmpty()) {
            user.setDepartmentId(new ObjectId(request.getDepartmentId()));
        }
        
        user.setActivo(true);
        user = userRepository.save(user);

        log.info("👤 Nuevo usuario registrado: {} con rol: {}", user.getEmail(), roleName);

        String accessToken = jwtTokenProvider.generateAccessToken(
            user.getId().toString(),
            user.getEmail(),
            roleName
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
            user.getId().toString(),
            user.getEmail()
        );

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh token inválido");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(new ObjectId(userId))
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(
            user.getId().toString(),
            user.getEmail(),
            getRoleName(user.getRoleId())
        );

        String newRefreshToken = jwtTokenProvider.generateRefreshToken(
            user.getId().toString(),
            user.getEmail()
        );

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(new ObjectId(userId))
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Contraseña actual incorrecta");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        log.info("Contraseña cambiada para usuario: {}", userId);
    }

    public void logout(String userId) {
        // Aquí podrías agregar lógica para invalidar tokens si lo deseas
        // Por ejemplo, guardar tokens en una lista negra
        log.info("Usuario {} ha cerrado sesión", userId);
    }

    private String getRoleName(ObjectId roleId) {
        if (roleId == null) {
            return "USER";
        }
        return roleRepository.findById(roleId)
            .map(Role::getNombre)
            .orElse("USER");
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getExpirationTimeMs() / 1000) // convertir a segundos
            .user(new AuthResponse.UserInfo(
                user.getId().toString(),
                user.getNombre(),
                user.getEmail(),
                getRoleName(user.getRoleId()),
                user.getDepartmentId() != null ? user.getDepartmentId().toString() : null
            ))
            .build();
    }
}
