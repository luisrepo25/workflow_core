package com.workflow.demo.domain.embedded;

import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;

import com.workflow.demo.domain.enums.NodeType;
import com.workflow.demo.domain.enums.ResponsableTipo;
import com.workflow.demo.domain.enums.ResponsableRole;

import lombok.Data;

@Data
public class WorkflowNode {
    @org.springframework.data.mongodb.core.mapping.Field("_id")
    private String id;
    private NodeType tipo;
    private String nombre;
    private String descripcion;
    private String laneId;
    
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.workflow.demo.config.JacksonConfig.ObjectIdDeserializer.class)
    private ObjectId departmentId;
    
    private ResponsableTipo responsableTipo;
    
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.workflow.demo.config.JacksonConfig.ObjectIdDeserializer.class)
    private ObjectId responsableUsuarioId;
    private ResponsableRole responsableRole;
    private Integer slaMinutos;
    private boolean permiteAdjuntos = false;
    private NodeForm form;
    private DecisionRule decisionRule;
    private Double posicionX;
    private Double posicionY;
}