package com.workflow.demo.domain.embedded;

import org.bson.types.ObjectId;

import lombok.Data;

@Data
public class Lane {
    @org.springframework.data.mongodb.core.mapping.Field("_id")
    private String id;
    private String nombre;
    private String descripcion;
    private String responsable;
    private String color;
    
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.workflow.demo.config.JacksonConfig.ObjectIdDeserializer.class)
    private ObjectId departmentId;
    
    private Integer orden;
}