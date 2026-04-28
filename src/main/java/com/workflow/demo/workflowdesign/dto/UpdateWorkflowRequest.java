package com.workflow.demo.workflowdesign.dto;

import com.workflow.demo.domain.enums.WorkflowStatus;

import lombok.Data;

@Data
public class UpdateWorkflowRequest {
    private String nombre;
    private String descripcion;
    private WorkflowStatus estado;
}