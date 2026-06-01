package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

/**
 * 工具验证结果。
 */
public class ValidationResult {

    private final boolean valid;
    private final String message;

    private ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static ValidationResult success(String message) {
        return new ValidationResult(true, message);
    }

    public static ValidationResult failure(String message) {
        return new ValidationResult(false, message);
    }

    public boolean isValid() { return valid; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + valid + ", message='" + message + "'}";
    }
}
