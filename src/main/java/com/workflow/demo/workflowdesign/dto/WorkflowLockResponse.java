package com.workflow.demo.workflowdesign.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkflowLockResponse {
    boolean locked;
    String workflowId;
    String enEdicionPor;
}