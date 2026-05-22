package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.ChatEventDto;
import io.leavesfly.jharness2.core.ChatService;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.web.dto.ChatRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final Path workspaceRoot;

    public ChatController(ChatService chatService,
                         @Value("${jharness2.workspace.root:/tmp/jharness2/workspaces}") String workspaceRoot) {
        this.chatService = chatService;
        this.workspaceRoot = Paths.get(workspaceRoot);
    }

    @PostMapping(value = "/{sessionId}/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(Authentication auth,
                                  @PathVariable String sessionId,
                                  @Valid @RequestBody ChatRequest request) {
        String username = auth.getName();
        SseEmitter emitter = new SseEmitter(300_000L);

        Path userWorkspace = workspaceRoot.resolve(username);
        UserContext context = new UserContext(
                username, sessionId, userWorkspace,
                request.getModel(), null, null);

        chatService.chat(context, request.getMessage(), (ChatEventDto dto) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(dto.getType())
                        .data(dto, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                logger.warn("SSE send failed for user={}, session={}", username, sessionId);
                emitter.completeWithError(e);
            }
        }).whenComplete((v, ex) -> {
            try {
                if (ex != null) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(ChatEventDto.error(ex.getMessage()), MediaType.APPLICATION_JSON));
                }
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> {
            logger.warn("SSE timeout for user={}, session={}", username, sessionId);
            chatService.cancelChat(username, sessionId);
        });

        return emitter;
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<?> cancelChat(Authentication auth, @PathVariable String sessionId) {
        chatService.cancelChat(auth.getName(), sessionId);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @PostMapping("/new")
    public ResponseEntity<?> newSession(Authentication auth) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }
}
