package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.storage.SessionStorageService;
import io.leavesfly.jharness2.storage.entity.SessionEntity;
import io.leavesfly.jharness2.web.dto.SessionInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionStorageService sessionStorageService;

    public SessionController(SessionStorageService sessionStorageService) {
        this.sessionStorageService = sessionStorageService;
    }

    @GetMapping
    public ResponseEntity<List<SessionInfo>> listSessions(Authentication auth) {
        List<SessionEntity> entities = sessionStorageService.listSessions(auth.getName());
        List<SessionInfo> result = entities.stream().map(this::toSessionInfo).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getSession(Authentication auth, @PathVariable String sessionId) {
        return sessionStorageService.findSession(auth.getName(), sessionId)
                .map(entity -> ResponseEntity.ok(Map.of(
                        "sessionId", entity.getSessionId(),
                        "model", entity.getModel() != null ? entity.getModel() : "",
                        "messageCount", entity.getMessageCount(),
                        "messages", entity.getMessagesJson() != null ? entity.getMessagesJson() : "[]",
                        "createdAt", entity.getCreatedAt().toString(),
                        "updatedAt", entity.getUpdatedAt().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> deleteSession(Authentication auth, @PathVariable String sessionId) {
        sessionStorageService.deleteSession(auth.getName(), sessionId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    private SessionInfo toSessionInfo(SessionEntity entity) {
        SessionInfo info = new SessionInfo();
        info.setSessionId(entity.getSessionId());
        info.setModel(entity.getModel());
        info.setTitle(entity.getTitle());
        info.setMessageCount(entity.getMessageCount());
        info.setCreatedAt(entity.getCreatedAt());
        info.setUpdatedAt(entity.getUpdatedAt());
        return info;
    }
}
