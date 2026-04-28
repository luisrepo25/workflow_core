package com.workflow.demo.workflowdesign.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponse {
    
    @JsonProperty("id")
    private String id;
    
    private String nombre;
    private String descripcion;
    private boolean activo;
    private Instant createdAt;
    private Instant updatedAt;
    
    /**
     * Convierte una entidad Department a DepartmentResponse
     */
    public static DepartmentResponse from(com.workflow.demo.domain.entity.Department department) {
        if (department == null) {
            return null;
        }
        
        return DepartmentResponse.builder()
            .id(department.getId() != null ? department.getId().toHexString() : null)
            .nombre(department.getNombre())
            .descripcion(department.getDescripcion())
            .activo(department.isActivo())
            .createdAt(department.getCreatedAt())
            .updatedAt(department.getUpdatedAt())
            .build();
    }
}
