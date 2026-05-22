package io.leavesfly.jharness2.storage;

import io.leavesfly.jharness2.storage.entity.MemoryEntity;
import io.leavesfly.jharness2.storage.repository.MemoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MemoryStorageService {

    private final MemoryRepository memoryRepository;

    public MemoryStorageService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    public void saveMemory(String userId, String project, String title,
                          String content, String category) {
        Instant now = Instant.now();
        MemoryEntity entity = memoryRepository
                .findByUserIdAndProjectAndTitle(userId, project, title)
                .orElseGet(() -> {
                    MemoryEntity newEntity = new MemoryEntity();
                    newEntity.setUserId(userId);
                    newEntity.setProject(project);
                    newEntity.setTitle(title);
                    newEntity.setCreatedAt(now);
                    return newEntity;
                });

        entity.setContent(content);
        entity.setCategory(category);
        entity.setUpdatedAt(now);
        memoryRepository.save(entity);
    }

    public List<MemoryEntity> listMemories(String userId, String project) {
        return memoryRepository.findByUserIdAndProject(userId, project);
    }

    public Optional<MemoryEntity> getMemory(String userId, String project, String title) {
        return memoryRepository.findByUserIdAndProjectAndTitle(userId, project, title);
    }

    public List<MemoryEntity> searchMemories(String userId, String project, String keyword) {
        return memoryRepository.searchByKeyword(userId, project, keyword);
    }

    public void deleteMemory(String userId, String project, String title) {
        memoryRepository.deleteByUserIdAndProjectAndTitle(userId, project, title);
    }
}
