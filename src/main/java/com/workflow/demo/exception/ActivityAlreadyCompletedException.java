package com.workflow.demo.exception;

public class ActivityAlreadyCompletedException extends RuntimeException {
    public ActivityAlreadyCompletedException(String message) {
        super(message);
    }
}