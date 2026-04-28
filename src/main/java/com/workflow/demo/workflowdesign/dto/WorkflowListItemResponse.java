package com.workflow.demo.workflowdesign.dto;

import java.time.Instant;

import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.WorkflowStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkflowListItemResponse {
    String id;
    String codigo;
    String nombre;
    String descripcion;
    WorkflowStatus estado;
    Instant createdAt;
    Instant updatedAt;
    String enEdicionPor;

    public static WorkflowListItemResponse from(Workflow workflow) {
        return WorkflowListItemResponse.builder()
            .id(workflow.getId() != null ? workflow.getId().toHexString() : null)
            .codigo(workflow.getCodigo())
            .nombre(workflow.getNombre())
            .descripcion(workflow.getDescripcion())
            .estado(workflow.getEstado())
            .createdAt(workflow.getCreatedAt())
            .updatedAt(workflow.getUpdatedAt())
            .enEdicionPor(workflow.getEnEdicionPor() != null ? workflow.getEnEdicionPor().toHexString() : null)
            .build();
    }
}