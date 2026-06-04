package com.linearizability.cloudstorage.objectstorage.ali;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.linearizability.cloudstorage.objectstorage.CloudStorageClient;
import com.linearizability.cloudstorage.objectstorage.CloudStorageConfig;
import com.linearizability.cloudstorage.objectstorage.CloudStorageFileInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云OSS客户端实现
 *
 * @author ZhangBoyuan
 * @since 2026-06-02
 */
@Slf4j
public class AliCloudStorageClient implements CloudStorageClient {

    private final CloudStorageConfig config;
    private final OSS ossClient;

    public AliCloudStorageClient(CloudStorageConfig config) {
        this.config = config;
        this.ossClient = new OSSClientBuilder().build(
                config.getEndpoint(),
                config.getAccessKeyId(),
                config.getAccessKeySecret()
        );
        log.info("阿里云OSS客户端初始化完成, endpoint={}, bucket={}", config.getEndpoint(), config.getBucketName());
    }

    @Override
    public String upload(String objectKey, InputStream stream, long contentLength) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (contentLength > 0) {
                metadata.setContentLength(contentLength);
            }
            ossClient.putObject(config.getBucketName(), objectKey, stream, metadata);
            String url = getUrl(objectKey);
            log.info("文件上传成功: {} -> {}", objectKey, url);
            return url;
        } catch (Exception e) {
            log.error("文件上传失败: {}", objectKey, e);
            throw new RuntimeException("阿里云OSS文件上传失败: " + objectKey, e);
        }
    }

    @Override
    public String upload(String objectKey, String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            File file = new File(filePath);
            return upload(objectKey, fis, file.length());
        } catch (Exception e) {
            log.error("文件上传失败: {} <- {}", objectKey, filePath, e);
            throw new RuntimeException("阿里云OSS文件上传失败", e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            OSSObject ossObject = ossClient.getObject(config.getBucketName(), objectKey);
            log.info("文件下载开始: {}", objectKey);
            return ossObject.getObjectContent();
        } catch (Exception e) {
            log.error("文件下载失败: {}", objectKey, e);
            throw new RuntimeException("阿里云OSS文件下载失败: " + objectKey, e);
        }
    }

    @Override
    public void download(String objectKey, String localPath) {
        try (InputStream is = download(objectKey)) {
            Path path = Path.of(localPath);
            Files.createDirectories(path.getParent());
            Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件下载完成: {} -> {}", objectKey, localPath);
        } catch (Exception e) {
            log.error("文件下载到本地失败: {} -> {}", objectKey, localPath, e);
            throw new RuntimeException("阿里云OSS文件下载到本地失败", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            ossClient.deleteObject(config.getBucketName(), objectKey);
            log.info("文件删除成功: {}", objectKey);
        } catch (Exception e) {
            log.error("文件删除失败: {}", objectKey, e);
            throw new RuntimeException("阿里云OSS文件删除失败: " + objectKey, e);
        }
    }

    @Override
    public CloudStorageFileInfo getFileInfo(String objectKey) {
        try {
            OSSObject object = ossClient.getObject(config.getBucketName(), objectKey);
            ObjectMetadata metadata = object.getObjectMetadata();
            return CloudStorageFileInfo.builder()
                    .objectKey(objectKey)
                    .fileName(extractFileName(objectKey))
                    .size(metadata.getContentLength())
                    .lastModified(metadata.getLastModified().getTime())
                    .directory(false)
                    .url(getUrl(objectKey))
                    .eTag(metadata.getETag())
                    .storageClass(metadata.getObjectStorageClass().name())
                    .build();
        } catch (Exception e) {
            log.error("获取文件信息失败: {}", objectKey, e);
            return null;
        }
    }

    @Override
    public List<CloudStorageFileInfo> list(String prefix) {
        try {
            List<CloudStorageFileInfo> result = new ArrayList<>();
            String nextMarker = null;
            ObjectListing listing;
            do {
                if (nextMarker == null) {
                    listing = ossClient.listObjects(config.getBucketName(), prefix);
                } else {
                    listing = ossClient.listObjects(config.getBucketName(), prefix);
                }
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    String key = summary.getKey();
                    if (key.equals(prefix)) {
                        continue;
                    }
                    result.add(buildFileInfo(summary));
                }
                nextMarker = listing.getNextMarker();
            } while (listing.isTruncated());
            log.info("列出对象成功, prefix={}, 数量={}", prefix, result.size());
            return result;
        } catch (Exception e) {
            log.error("列出对象失败, prefix={}", prefix, e);
            throw new RuntimeException("阿里云OSS列出对象失败", e);
        }
    }

    @Override
    public List<CloudStorageFileInfo> listDirect(String prefix) {
        try {
            String delimiter = "/";
            List<CloudStorageFileInfo> result = new ArrayList<>();
            String nextMarker = null;
            ObjectListing listing;

            do {
                com.aliyun.oss.model.ListObjectsRequest request =
                        new com.aliyun.oss.model.ListObjectsRequest(config.getBucketName())
                                .withPrefix(prefix)
                                .withDelimiter(delimiter)
                                .withMarker(nextMarker);

                listing = ossClient.listObjects(request);

                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    String key = summary.getKey();
                    if (key.equals(prefix)) {
                        continue;
                    }
                    result.add(buildFileInfo(summary));
                }

                for (String commonPrefix : listing.getCommonPrefixes()) {
                    result.add(CloudStorageFileInfo.builder()
                            .objectKey(commonPrefix)
                            .fileName(extractFileName(commonPrefix.substring(0, commonPrefix.length() - 1)))
                            .size(0)
                            .directory(true)
                            .url(getUrl(commonPrefix))
                            .build());
                }

                nextMarker = listing.getNextMarker();
            } while (listing.isTruncated());

            log.info("列出直接子项成功, prefix={}, 数量={}", prefix, result.size());
            return result;
        } catch (Exception e) {
            log.error("列出直接子项失败, prefix={}", prefix, e);
            throw new RuntimeException("阿里云OSS列出直接子项失败", e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            return ossClient.doesObjectExist(config.getBucketName(), objectKey);
        } catch (Exception e) {
            log.error("判断文件是否存在失败: {}", objectKey, e);
            throw new RuntimeException("阿里云OSS判断文件是否存在失败", e);
        }
    }

    @Override
    public String getUrl(String objectKey) {
        return config.getEndpoint() + "/" + config.getBucketName() + "/" + objectKey;
    }

    @Override
    public void close() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("阿里云OSS客户端已关闭");
        }
    }

    private CloudStorageFileInfo buildFileInfo(OSSObjectSummary summary) {
        return CloudStorageFileInfo.builder()
                .objectKey(summary.getKey())
                .fileName(extractFileName(summary.getKey()))
                .size(summary.getSize())
                .lastModified(summary.getLastModified().getTime())
                .directory(false)
                .url(getUrl(summary.getKey()))
                .eTag(summary.getETag())
                .storageClass(summary.getStorageClass())
                .build();
    }

    private static String extractFileName(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return "";
        }
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
    }
}