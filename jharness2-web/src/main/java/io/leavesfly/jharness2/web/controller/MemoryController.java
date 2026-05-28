package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.spi.VectorMemorySearch;
import io.leavesfly.jharness2.core.spi.VectorSearchResult;
import io.leavesfly.jharness2.storage.entity.MemoryEntity;
import io.leavesfly.jharness2.storage.repository.MemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 记忆管理接口 —— 列表、关键词搜索、语义搜索、删除。
 */
@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryRepository memoryRepository;

    @Autowired(required = false)
    private VectorMemorySearch vectorMemorySearch;

    public MemoryController(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @GetMapping
    public ResponseEntity<?> listMemories(Authentication auth,
                                           @RequestParam(defaultValue = "default") String project) {
        String userId = auth.getName();
        List<MemoryEntity> memories = memoryRepository.findByUserIdAndProject(userId, project);
        return ResponseEntity.ok(Map.of("userId", userId, "project", project, "count", memories.size(), "memories", memories));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchMemories(Authentication auth,
                                             @RequestParam String query,
                                             @RequestParam(defaultValue = "default") String project,
                                             @RequestParam(defaultValue = "keyword") String mode,
                                             @RequestParam(defaultValue = "10") int topK) {
        String userId = auth.getName();

        if ("vector".equals(mode) && vectorMemorySearch != null) {
            List<VectorSearchResult> results = vectorMemorySearch.search(userId, project, query, topK);
            return ResponseEntity.ok(Map.of("mode", "vector", "count", results.size(), "results", results));
        }

        // fallback: 关键词搜索
        List<MemoryEntity> results = memoryRepository.searchByKeyword(userId, project, query);
        return ResponseEntity.ok(Map.of("mode", "keyword", "count", results.size(), "results", results));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> listByCategory(Authentication auth,
                                             @RequestParam(defaultValue = "default") String project,
                                             @RequestParam String category) {
        String userId = auth.getName();
        List<MemoryEntity> memories = memoryRepository.findByUserIdAndProjectAndCategory(userId, project, category);
        return ResponseEntity.ok(Map.of("category", category, "count", memories.size(), "memories", memories));
    }

    @DeleteMapping("/{project}/{title}")
    public ResponseEntity<?> deleteMemory(Authentication auth,
                                           @PathVariable String project,
                                           @PathVariable String title) {
        String userId = auth.getName();
        memoryRepository.deleteByUserIdAndProjectAndTitle(userId, project, title);
        if (vectorMemorySearch != null) {
            vectorMemorySearch.delete(project + ":" + title);
        }
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
