package com.workflow.demo.domain.embedded;

import org.springframework.data.mongodb.core.mapping.Field;

import com.workflow.demo.domain.enums.EdgeType;

import lombok.Data;

@Data
public class WorkflowEdge {
    @Field("from")
    private String fromNodeId;

    @Field("to")
    private String toNodeId;

    private EdgeType tipo;
    private String label;
}