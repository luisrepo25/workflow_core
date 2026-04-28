package com.workflow.demo.workflowdesign.dto;

import java.time.Instant;
import java.util.List;

import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.WorkflowStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkflowDetailResponse {
    String id;
    String codigo;
    String nombre;
    String descripcion;
    WorkflowStatus estado;
    String createdBy;
    String enEdicionPor;
    Instant createdAt;
    Instant updatedAt;
    List<Lane> lanes;
    List<WorkflowNode> nodes;
    List<WorkflowEdge> edges;

    public static WorkflowDetailResponse from(Workflow workflow) {
        return WorkflowDetailResponse.builder()
            .id(workflow.getId() != null ? workflow.getId().toHexString() : null)
            .codigo(workflow.getCodigo())
            .nombre(workflow.getNombre())
            .descripcion(workflow.getDescripcion())
            .estado(workflow.getEstado())
            .createdBy(workflow.getCreatedBy() != null ? workflow.getCreatedBy().toHexString() : null)
            .enEdicionPor(workflow.getEnEdicionPor() != null ? workflow.getEnEdicionPor().toHexString() : null)
            .createdAt(workflow.getCreatedAt())
            .updatedAt(workflow.getUpdatedAt())
            .lanes(workflow.getLanes())
            .nodes(workflow.getNodes())
            .edges(workflow.getEdges())
            .build();
    }
}