package io.leavesfly.jharness2.core;

public class EngineLimitExceededException extends RuntimeException {
    public EngineLimitExceededException(String message) {
        super(message);
    }
}
