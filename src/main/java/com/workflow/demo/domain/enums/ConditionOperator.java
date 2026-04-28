package com.workflow.demo.domain.enums;

import lombok.Getter;

@Getter
public enum ConditionOperator {
    EQUALS("=="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    GREATER_EQUAL_THAN(">="),
    LESS_THAN("<"),
    LESS_EQUAL_THAN("<="),
    CONTAINS("contains"),
    STARTS_WITH("startsWith"),
    ENDS_WITH("endsWith");

    private final String symbol;

    ConditionOperator(String symbol) {
        this.symbol = symbol;
    }

    public static ConditionOperator fromSymbol(String symbol) {
        for (ConditionOperator op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown operator symbol: " + symbol);
    }
}
