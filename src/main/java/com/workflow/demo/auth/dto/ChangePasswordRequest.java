package com.workflow.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    
    @NotBlank(message = "Contraseña actual es requerida")
    private String currentPassword;
    
    @NotBlank(message = "Nueva contraseña es requerida")
    @Size(min = 6, message = "Nueva contraseña debe tener al menos 6 caracteres")
    private String newPassword;
}
