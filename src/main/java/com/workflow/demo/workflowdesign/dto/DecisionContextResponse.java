package com.workflow.demo.workflowdesign.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DecisionContextResponse {
    private String decisionNodeId;
    private List<String> incomingNodeIds;
    private List<DecisionContextField> fields;
    private Map<String, List<String>> operatorsByType;

    @Data
    @Builder
    public static class DecisionContextField {
        private String fieldId;
        private String label;
        private String type;
        private String sourceNodeId;
    }
}
