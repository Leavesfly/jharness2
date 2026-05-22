package io.leavesfly.jharness2.storage;

import io.leavesfly.jharness2.storage.entity.MemoryEntity;
import io.leavesfly.jharness2.storage.repository.MemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MemoryStorageService {

    private final MemoryRepository memoryRepository;

    public MemoryStorageService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Transactional
    public void saveMemory(String userId, String project, String title,
                          String content, String category) {
        MemoryEntity entity = memoryRepository
                .findByUserIdAndProjectAndTitle(userId, project, title)
                .orElseGet(() -> {
                    MemoryEntity newEntity = new MemoryEntity();
                    newEntity.setUserId(userId);
                    newEntity.setProject(project);
                    newEntity.setTitle(title);
                    return newEntity;
                });

        entity.setContent(content);
        entity.setCategory(category);
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

    @Transactional
    public void deleteMemory(String userId, String project, String title) {
        memoryRepository.deleteByUserIdAndProjectAndTitle(userId, project, title);
    }
}
