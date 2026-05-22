package io.leavesfly.jharness2.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import io.leavesfly.jharness2.storage.entity.SessionEntity;
import io.leavesfly.jharness2.storage.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SessionStorageService implements SessionPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(SessionStorageService.class);
    private final SessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public SessionStorageService(SessionRepository sessionRepository, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    public void saveSession(String userId, String sessionId, String model,
                           List<?> messages, long inputTokens, long outputTokens) {
        Instant now = Instant.now();
        SessionEntity entity = sessionRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseGet(() -> {
                    SessionEntity newEntity = new SessionEntity();
                    newEntity.setUserId(userId);
                    newEntity.setSessionId(sessionId);
                    newEntity.setCreatedAt(now);
                    return newEntity;
                });

        entity.setModel(model);
        entity.setMessageCount(messages.size());
        entity.setInputTokens(inputTokens);
        entity.setOutputTokens(outputTokens);
        entity.setUpdatedAt(now);

        try {
            entity.setMessagesJson(objectMapper.writeValueAsString(messages));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize messages for session {}", sessionId, e);
            throw new RuntimeException("Failed to serialize messages", e);
        }

        sessionRepository.save(entity);
    }

    public Optional<SessionEntity> findSession(String userId, String sessionId) {
        return sessionRepository.findByUserIdAndSessionId(userId, sessionId);
    }

    public List<SessionEntity> listSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public void deleteSession(String userId, String sessionId) {
        sessionRepository.deleteByUserIdAndSessionId(userId, sessionId);
    }
}
