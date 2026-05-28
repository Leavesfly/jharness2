package io.leavesfly.jharness2.storage.impl;

import io.leavesfly.jharness2.core.spi.ArtifactMeta;
import io.leavesfly.jharness2.core.spi.ArtifactStore;
import io.leavesfly.jharness2.storage.entity.ArtifactEntity;
import io.leavesfly.jharness2.storage.repository.ArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JpaArtifactStore implements ArtifactStore {

    private static final Logger logger = LoggerFactory.getLogger(JpaArtifactStore.class);
    private final ArtifactRepository repository;

    public JpaArtifactStore(ArtifactRepository repository) {
        this.repository = repository;
    }

    @Override
    public void register(ArtifactMeta artifact) {
        ArtifactEntity entity = new ArtifactEntity();
        entity.setId(artifact.artifactId());
        entity.setUserId(artifact.userId());
        entity.setSessionId(artifact.sessionId());
        entity.setFilePath(artifact.filePath());
        entity.setMimeType(artifact.mimeType());
        entity.setSizeBytes(artifact.sizeBytes());
        entity.setChecksum(artifact.checksum());
        entity.setCreatedAt(artifact.createdAt());
        repository.save(entity);
        logger.debug("Artifact registered: id={}, path={}", artifact.artifactId(), artifact.filePath());
    }

    @Override
    public List<ArtifactMeta> listBySession(String userId, String sessionId) {
        return repository.findByUserIdAndSessionId(userId, sessionId).stream()
                .map(this::toArtifactMeta)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactMeta> listRecent(String userId, int limit) {
        return repository.findRecentByUserId(userId, limit).stream()
                .map(this::toArtifactMeta)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ArtifactMeta> findById(String artifactId) {
        return repository.findById(artifactId).map(this::toArtifactMeta);
    }

    @Override
    public void delete(String artifactId) {
        repository.deleteByArtifactId(artifactId);
        logger.debug("Artifact deleted: id={}", artifactId);
    }

    private ArtifactMeta toArtifactMeta(ArtifactEntity entity) {
        return new ArtifactMeta(
                entity.getId(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getFilePath(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                entity.getChecksum(),
                entity.getCreatedAt()
        );
    }
}
