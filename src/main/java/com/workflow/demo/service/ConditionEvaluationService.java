package com.workflow.demo.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.workflow.demo.domain.embedded.DecisionRule;
import com.workflow.demo.domain.enums.ConditionOperator;

@Service
public class ConditionEvaluationService {

    private static final Pattern SIMPLE_EXPRESSION = Pattern
        .compile("^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*(==|!=|>=|<=|>|<|contains|startsWith|endsWith)\\s*(.+?)\\s*$");

    public boolean evaluate(String condition, Map<String, Object> context) {
        if (condition == null || condition.isBlank()) {
            return false;
        }

        Matcher matcher = SIMPLE_EXPRESSION.matcher(condition);
        if (!matcher.matches()) {
            return false;
        }

        String field = matcher.group(1);
        String operator = matcher.group(2);
        String rightRaw = matcher.group(3);

        Object leftValue = context.get(field);
        Object rightValue = parseLiteral(rightRaw);

        return compare(leftValue, operator, rightValue);
    }

    public boolean evaluateRule(DecisionRule rule, Map<String, Object> context) {
        if (rule == null || rule.getField() == null || rule.getOperator() == null) {
            return false;
        }

        Object leftValue = context.get(rule.getField());
        Object rightValue = parseLiteral(rule.getValue());

        return compare(leftValue, rule.getOperator(), rightValue);
    }

    private Object parseLiteral(String raw) {
        if (raw == null) return null;
        String value = raw.trim();

        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.valueOf(value);
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private boolean compare(Object leftValue, ConditionOperator operator, Object rightValue) {
        if (leftValue == null) {
            return switch (operator) {
                case EQUALS -> rightValue == null;
                case NOT_EQUALS -> rightValue != null;
                default -> false;
            };
        }

        if (operator == ConditionOperator.CONTAINS || operator == ConditionOperator.STARTS_WITH || operator == ConditionOperator.ENDS_WITH) {
            String left = String.valueOf(leftValue);
            String right = String.valueOf(rightValue);
            return switch (operator) {
                case CONTAINS -> left.contains(right);
                case STARTS_WITH -> left.startsWith(right);
                case ENDS_WITH -> left.endsWith(right);
                default -> false;
            };
        }

        if (leftValue instanceof Boolean || rightValue instanceof Boolean) {
            if (!(operator == ConditionOperator.EQUALS || operator == ConditionOperator.NOT_EQUALS)) {
                return false;
            }
            boolean left = Boolean.parseBoolean(String.valueOf(leftValue));
            boolean right = Boolean.parseBoolean(String.valueOf(rightValue));
            return operator == ConditionOperator.EQUALS ? left == right : left != right;
        }

        if (isNumericCandidate(leftValue) && isNumericCandidate(rightValue)) {
            try {
                BigDecimal left = toBigDecimal(leftValue);
                BigDecimal right = toBigDecimal(rightValue);
                int comparison = left.compareTo(right);
                return switch (operator) {
                    case EQUALS -> comparison == 0;
                    case NOT_EQUALS -> comparison != 0;
                    case GREATER_THAN -> comparison > 0;
                    case LESS_THAN -> comparison < 0;
                    case GREATER_EQUAL_THAN -> comparison >= 0;
                    case LESS_EQUAL_THAN -> comparison <= 0;
                    default -> false;
                };
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        String left = String.valueOf(leftValue);
        String right = String.valueOf(rightValue);
        int comparison = left.compareTo(right);
        return switch (operator) {
            case EQUALS -> left.equals(right);
            case NOT_EQUALS -> !left.equals(right);
            case GREATER_THAN -> comparison > 0;
            case LESS_THAN -> comparison < 0;
            case GREATER_EQUAL_THAN -> comparison >= 0;
            case LESS_EQUAL_THAN -> comparison <= 0;
            default -> false;
        };
    }

    private boolean compare(Object leftValue, String operatorStr, Object rightValue) {
        ConditionOperator operator;
        try {
            operator = ConditionOperator.fromSymbol(operatorStr);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return compare(leftValue, operator, rightValue);
    }

    private boolean isNumericCandidate(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number || value instanceof BigDecimal) {
            return true;
        }
        try {
            new BigDecimal(String.valueOf(value));
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value).trim());
 
    }
}