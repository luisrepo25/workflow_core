package com.workflow.demo.domain.embedded;

import com.workflow.demo.domain.enums.ConditionOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRule {
    private String field;
    private ConditionOperator operator;
    private String value;
    private String onTrueDestinoNodeId;
    private String onFalseDestinoNodeId;
}