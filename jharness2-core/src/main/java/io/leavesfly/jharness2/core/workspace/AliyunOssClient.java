package io.leavesfly.jharness2.core.workspace;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云 OSS 客户端实现。
 */
public class AliyunOssClient implements OssClient {

    private static final Logger logger = LoggerFactory.getLogger(AliyunOssClient.class);

    private final OSS oss;
    private final String bucketName;

    public AliyunOssClient(String endpoint, String accessKeyId, String accessKeySecret, String bucketName) {
        this.oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        this.bucketName = bucketName;
        logger.info("AliyunOssClient initialized: endpoint={}, bucket={}", endpoint, bucketName);
    }

    @Override
    public void putObject(String key, Path localFile) {
        oss.putObject(bucketName, key, localFile.toFile());
    }

    @Override
    public InputStream getObject(String key) {
        return oss.getObject(bucketName, key).getObjectContent();
    }

    @Override
    public List<String> listObjects(String prefix) {
        List<String> keys = new ArrayList<>();
        String nextMarker = null;
        boolean truncated = true;

        while (truncated) {
            ListObjectsRequest request = new ListObjectsRequest(bucketName);
            request.setPrefix(prefix);
            request.setMaxKeys(1000);
            if (nextMarker != null) {
                request.setMarker(nextMarker);
            }

            ObjectListing listing = oss.listObjects(request);
            for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                keys.add(summary.getKey());
            }
            truncated = listing.isTruncated();
            nextMarker = listing.getNextMarker();
        }

        return keys;
    }

    @Override
    public void deleteObject(String key) {
        oss.deleteObject(bucketName, key);
    }

    public void shutdown() {
        oss.shutdown();
    }
}
