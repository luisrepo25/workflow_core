package com.workflow.demo.workflowdesign.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LockWorkflowRequest {
    @NotBlank
    private String userId;
}