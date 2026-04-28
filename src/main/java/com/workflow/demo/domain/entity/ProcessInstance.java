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

import com.workflow.demo.domain.embedded.HistoryEvent;
import com.workflow.demo.domain.embedded.ProcessActivity;
import com.workflow.demo.domain.embedded.SummaryData;
import com.workflow.demo.domain.embedded.WorkflowSnapshot;
import com.workflow.demo.domain.enums.ProcessStatus;

import lombok.Data;

@Data
@Document(collection = "process_instances")
public class ProcessInstance {
    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private String codigo;

    @Indexed
    private ObjectId workflowId;

    @Indexed
    private ObjectId clienteId;

    @Indexed
    private ProcessStatus estado;

    private List<String> currentNodeIds = new ArrayList<>();
    private SummaryData datosResumen;
    private List<ProcessActivity> actividades = new ArrayList<>();
    private List<HistoryEvent> historial = new ArrayList<>();
    
    // Snapshot del workflow al momento de crear el proceso (Fase 8)
    private WorkflowSnapshot workflowSnapshot;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant finishedAt;
}