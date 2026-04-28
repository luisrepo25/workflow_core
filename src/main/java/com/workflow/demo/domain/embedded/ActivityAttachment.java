package com.workflow.demo.domain.embedded;

import org.bson.types.ObjectId;

import lombok.Data;

@Data
public class ActivityAttachment {
    private ObjectId fileId;
    private String nombre;
    private String url;
}