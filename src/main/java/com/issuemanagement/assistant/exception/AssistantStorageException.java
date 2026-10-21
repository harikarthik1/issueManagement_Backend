package com.issuemanagement.assistant.exception;

public class AssistantStorageException extends RuntimeException {
    public AssistantStorageException(String message) {
        super(message);
    }

    public AssistantStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
