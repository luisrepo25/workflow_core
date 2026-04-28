package com.workflow.demo.workflowdesign.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.workflow.demo.domain.embedded.DecisionRule;

import lombok.Data;

@Data
public class DecisionRulesSimulateRequest {
    private Map<String, Object> input = new HashMap<>();
    private DecisionRule decisionRule;
    private String mode; // binary | standard
}
