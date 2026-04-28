package com.workflow.demo.workflowdesign.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkflowValidationResponse {
    boolean valid;
    @Builder.Default
    List<String> errors = new ArrayList<>();
    @Builder.Default
    List<String> warnings = new ArrayList<>();
}