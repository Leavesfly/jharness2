package io.leavesfly.jharness2.core.workspace;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 对象存储客户端抽象接口。
 * <p>
 * 屏蔽具体云厂商 SDK 差异（Aliyun OSS / AWS S3 / MinIO 等），
 * 由具体实现类适配对应 SDK。
 */
public interface OssClient {

    /**
     * 上传本地文件到对象存储。
     */
    void putObject(String key, Path localFile);

    /**
     * 从对象存储下载文件，返回输入流（调用方负责关闭）。
     */
    InputStream getObject(String key);

    /**
     * 列出指定前缀下的所有对象 key。
     */
    List<String> listObjects(String prefix);

    /**
     * 删除指定 key 的对象。
     */
    void deleteObject(String key);
}
