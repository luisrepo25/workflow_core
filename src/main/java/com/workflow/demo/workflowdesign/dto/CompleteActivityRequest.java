package com.workflow.demo.workflowdesign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para completar una actividad
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteActivityRequest {
    
    @NotNull(message = "respuestaFormulario es requerido")
    @JsonProperty("respuestaFormulario")
    private java.util.Map<String, Object> respuestaFormulario;
    
    // Optional: comentarios del usuario
    @JsonProperty("comentarios")
    private String comentarios;
}
