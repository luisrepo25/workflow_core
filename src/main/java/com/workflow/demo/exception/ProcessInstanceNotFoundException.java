package com.workflow.demo.exception;

public class ProcessInstanceNotFoundException extends RuntimeException {
    public ProcessInstanceNotFoundException(String message) {
        super(message);
    }
}