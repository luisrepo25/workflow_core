package com.workflow.demo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    @NotBlank(message = "Nombre es requerido")
    @Size(min = 2, max = 100)
    private String nombre;
    
    @NotBlank(message = "Email es requerido")
    @Email(message = "Email debe ser válido")
    private String email;
    
    @NotBlank(message = "Contraseña es requerida")
    @Size(min = 6, message = "Contraseña debe tener al menos 6 caracteres")
    private String password;
    
    private String telefono;
    
    // departmentId puede ser null inicialmente
    private String departmentId;
    
    // ✅ Nuevo: Rol que el usuario desea asumir (Diseñador, Funcionario, Cliente)
    @NotBlank(message = "Rol es requerido (Diseñador, Funcionario, Cliente, Admin)")
    private String role;  // Será validado contra RoleEnum
}

