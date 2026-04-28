package com.workflow.demo.domain.embedded;

import org.bson.types.ObjectId;

import lombok.Data;

@Data
public class NotificationReference {
    private ObjectId processInstanceId;
    private ObjectId workflowId;
    private String nodeId;
}