package com.workflow.demo.workflowdesign.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDepartmentRequest {

    @NotBlank(message = "Nombre es requerido")
    private String nombre;

    private String descripcion;

    private Boolean activo;
}
