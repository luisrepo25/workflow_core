package com.workflow.demo.workflowdesign.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DecisionRulesSimulateResponse {
    private boolean matched;
    private String matchedRule;
    private String destinoNodeId;
    private List<String> trace;

    public static DecisionRulesSimulateResponse of(
            boolean matched,
            String matchedRule,
            String destinoNodeId,
            List<String> trace) {
        return DecisionRulesSimulateResponse.builder()
            .matched(matched)
            .matchedRule(matchedRule)
            .destinoNodeId(destinoNodeId)
            .trace(trace == null ? new ArrayList<>() : new ArrayList<>(trace))
            .build();
    }
}
