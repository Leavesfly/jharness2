package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.EngineLimitException;
import io.leavesfly.jharness2.core.distributed.EngineOwnedByOtherNodeException;
import io.leavesfly.jharness2.core.quota.QuotaExceededException;
import io.leavesfly.jharness2.core.ratelimit.RateLimitExceededException;
import io.leavesfly.jharness2.engine.EngineBusyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EngineLimitException.class)
    public ResponseEntity<?> handleEngineLimitExceeded(EngineLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * 限流/并发超限：返回 429 并带 Retry-After，使客户端能区分“应重试”与“服务出错”。
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("error", ex.getMessage(),
                        "retryAfterSeconds", ex.getRetryAfterSeconds()));
    }

    /**
     * 配额（如每日 token）超限：返回 429，并告知超限类型。
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<?> handleQuotaExceeded(QuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage(), "quotaType", ex.getQuotaType()));
    }

    @ExceptionHandler(EngineBusyException.class)
    public ResponseEntity<?> handleEngineBusy(EngineBusyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "当前会话正在处理另一条消息，请等待完成后重试"));
    }

    /**
     * 分布式模式下会话属主在其他节点：返回 409 而非 500，
     * 并提示客户端重试（需配合会话亲和路由/网关转发才能命中属主节点）。
     */
    @ExceptionHandler(EngineOwnedByOtherNodeException.class)
    public ResponseEntity<?> handleEngineOwnedByOtherNode(EngineOwnedByOtherNodeException ex) {
        logger.warn("Request hit non-owner node: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(Map.of("error", "会话正由另一节点处理，请稍后重试",
                        "code", "ENGINE_OWNED_BY_OTHER_NODE"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(NoResourceFoundException ex) {
        logger.debug("Resource not found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not found: " + ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
