package com.workflow.demo.workflowdesign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para invitar un colaborador a un workflow
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteCollaboratorRequest {
    
    @NotBlank(message = "email es requerido")
    @Email(message = "email debe tener un formato válido")
    @JsonProperty("email")
    private String email;
    
    @NotBlank(message = "role es requerido (DESIGNER o VIEWER)")
    @JsonProperty("role")
    private String role;  // "DESIGNER" o "VIEWER"
}
