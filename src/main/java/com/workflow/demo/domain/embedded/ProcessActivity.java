package com.workflow.demo.domain.embedded;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import com.workflow.demo.domain.enums.ActivityStatus;
import com.workflow.demo.domain.enums.ResponsableTipo;

import lombok.Data;

@Data
public class ProcessActivity {
    private String actividadId;
    private String nodeId;
    private String nombre;
    private ResponsableTipo responsableTipo;
    
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.workflow.demo.config.JacksonConfig.ObjectIdDeserializer.class)
    private ObjectId usuarioId;
    
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.workflow.demo.config.JacksonConfig.ObjectIdDeserializer.class)
    private ObjectId departmentId;
    private ActivityStatus estado;
    private Instant fechaInicio;
    private Instant fechaFin;
    private Map<String, Object> respuestaFormulario = new HashMap<>();
    private List<ActivityAttachment> adjuntos = new ArrayList<>();
    private String observacion;
    private Integer iteracion = 1;
}