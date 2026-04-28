package com.workflow.demo.workflowdesign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para crear un nuevo ProcessInstance (trámite)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProcessInstanceRequest {
    
    @NotBlank(message = "workflowId es requerido")
    @JsonProperty("workflowId")
    private String workflowId;
    
    @NotBlank(message = "clienteId es requerido")
    @JsonProperty("clienteId")
    private String clienteId;
    
    // Optional: datos iniciales del formulario para el primer nodo
    @JsonProperty("datosIniciales")
    private java.util.Map<String, Object> datosIniciales;
}
