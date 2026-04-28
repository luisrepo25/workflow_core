package com.workflow.demo.workflowdesign.dto;

import com.workflow.demo.domain.enums.WorkflowStatus;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWorkflowRequest {
    @NotBlank
    private String codigo;

    @NotBlank
    private String nombre;

    private String descripcion;
    private WorkflowStatus estado = WorkflowStatus.borrador;
}