package com.workflow.demo.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "Email es requerido")
    @Email(message = "Email debe ser válido")
    private String email;
    
    @NotBlank(message = "Contraseña es requerida")
    @Size(min = 6, message = "Contraseña debe tener al menos 6 caracteres")
    private String password;
    
    @JsonAlias({"fcmToken"})
    @JsonProperty("fcm_token")
    private String fcmToken;
}
