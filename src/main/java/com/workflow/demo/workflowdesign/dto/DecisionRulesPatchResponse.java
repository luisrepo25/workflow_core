package com.workflow.demo.workflowdesign.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.workflow.demo.domain.embedded.DecisionRule;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DecisionRulesPatchResponse {
    private String nodeId;
    private Instant updatedAt;
    private DecisionRule decisionRule;

    public static DecisionRulesPatchResponse of(String nodeId, Instant updatedAt, DecisionRule decisionRule) {
        return DecisionRulesPatchResponse.builder()
            .nodeId(nodeId)
            .updatedAt(updatedAt)
            .decisionRule(decisionRule)
            .build();
    }
}
