package com.workflow.demo.workflowdesign.dto;

import java.util.ArrayList;
import java.util.List;

import com.workflow.demo.domain.embedded.DecisionRule;

import lombok.Data;

@Data
public class DecisionRulesValidateRequest {
    private DecisionRule decisionRule;
    private String mode; // binary | standard
}
