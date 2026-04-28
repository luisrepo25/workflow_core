package com.workflow.demo.workflowdesign.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DecisionRulesValidationResult {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;

    public static DecisionRulesValidationResult of(List<String> errors, List<String> warnings) {
        List<String> safeErrors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        return DecisionRulesValidationResult.builder()
            .valid(safeErrors.isEmpty())
            .errors(safeErrors)
            .warnings(safeWarnings)
            .build();
    }
}
