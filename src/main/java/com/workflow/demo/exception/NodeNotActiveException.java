package com.workflow.demo.exception;

public class NodeNotActiveException extends RuntimeException {
    public NodeNotActiveException(String message) {
        super(message);
    }
}