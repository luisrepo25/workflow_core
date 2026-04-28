package com.workflow.demo.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.enums.WorkflowStatus;

import lombok.Data;

@Data
@Document(collection = "workflows")
public class Workflow {
    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private String codigo;

    private String nombre;
    private String descripcion;
    private WorkflowStatus estado;

    @Indexed
    private ObjectId enEdicionPor;

    private List<Lane> lanes = new ArrayList<>();
    private List<WorkflowNode> nodes = new ArrayList<>();
    private List<WorkflowEdge> edges = new ArrayList<>();

    @Indexed
    private ObjectId createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}