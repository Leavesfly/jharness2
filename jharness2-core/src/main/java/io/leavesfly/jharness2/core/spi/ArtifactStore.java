package io.leavesfly.jharness2.core.spi;

import java.util.List;
import java.util.Optional;

/**
 * 文件工件元数据存储 SPI。
 * <p>
 * 记录 Agent 产出的文件索引,支持按会话查询产物列表。
 * 与 WorkspaceStorage 配合：WorkspaceStorage 管理文件内容,
 * ArtifactStore 管理元数据索引。
 */
public interface ArtifactStore {

    /** 记录一个工件 */
    void register(ArtifactMeta artifact);

    /** 按会话查询工件列表 */
    List<ArtifactMeta> listBySession(String userId, String sessionId);

    /** 按用户查询最近工件 */
    List<ArtifactMeta> listRecent(String userId, int limit);

    /** 获取单个工件信息 */
    Optional<ArtifactMeta> findById(String artifactId);

    /** 删除工件记录（不删除实际文件） */
    void delete(String artifactId);
}
