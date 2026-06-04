package com.linearizability.cloudstorage.objectstorage.s3;

import com.linearizability.cloudstorage.objectstorage.CloudStorageClient;
import com.linearizability.cloudstorage.objectstorage.CloudStorageConfig;
import com.linearizability.cloudstorage.objectstorage.CloudStorageFileInfo;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * S3协议客户端实现，兼容AWS S3和MinIO
 *
 * @author ZhangBoyuan
 * @since 2026-06-02
 */
@Slf4j
public class S3CloudStorageClient implements CloudStorageClient {

    private final CloudStorageConfig config;
    private final S3Client s3Client;

    public S3CloudStorageClient(CloudStorageConfig config) {
        this.config = config;
        software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKeyId(), config.getAccessKeySecret())
                ))
                .region(Region.of(config.getRegion() != null ? config.getRegion() : "us-east-1"));

        if (config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
            builder.endpointOverride(java.net.URI.create(config.getEndpoint()));
        }

        if (config.isPathStyleAccess()) {
            builder.serviceConfiguration(configBuilder ->
                    configBuilder.pathStyleAccessEnabled(true)
            );
        }

        this.s3Client = builder.build();
        log.info("S3客户端初始化完成, endpoint={}, bucket={}, pathStyleAccess={}",
                config.getEndpoint(), config.getBucketName(), config.isPathStyleAccess());
    }

    @Override
    public String upload(String objectKey, InputStream stream, long contentLength) {
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey);

            if (contentLength > 0) {
                requestBuilder.contentLength(contentLength);
            }

            RequestBody requestBody;
            if (contentLength > 0) {
                requestBody = RequestBody.fromInputStream(stream, contentLength);
            } else {
                requestBody = RequestBody.fromInputStream(stream, stream.available());
            }

            s3Client.putObject(requestBuilder.build(), requestBody);
            String url = getUrl(objectKey);
            log.info("文件上传成功: {} -> {}", objectKey, url);
            return url;
        } catch (Exception e) {
            log.error("文件上传失败: {}", objectKey, e);
            throw new RuntimeException("S3文件上传失败: " + objectKey, e);
        }
    }

    @Override
    public String upload(String objectKey, String filePath) {
        try {
            File file = new File(filePath);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey)
                    .contentLength(file.length())
                    .build();

            s3Client.putObject(request, RequestBody.fromFile(file));
            String url = getUrl(objectKey);
            log.info("文件上传成功: {} -> {}", objectKey, url);
            return url;
        } catch (Exception e) {
            log.error("文件上传失败: {} <- {}", objectKey, filePath, e);
            throw new RuntimeException("S3文件上传失败", e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey)
                    .build();

            InputStream inputStream = s3Client.getObject(request);
            log.info("文件下载开始: {}", objectKey);
            return inputStream;
        } catch (Exception e) {
            log.error("文件下载失败: {}", objectKey, e);
            throw new RuntimeException("S3文件下载失败: " + objectKey, e);
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
            throw new RuntimeException("S3文件下载到本地失败", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(request);
            log.info("文件删除成功: {}", objectKey);
        } catch (Exception e) {
            log.error("文件删除失败: {}", objectKey, e);
            throw new RuntimeException("S3文件删除失败: " + objectKey, e);
        }
    }

    @Override
    public CloudStorageFileInfo getFileInfo(String objectKey) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return CloudStorageFileInfo.builder()
                    .objectKey(objectKey)
                    .fileName(extractFileName(objectKey))
                    .size(response.contentLength())
                    .lastModified(response.lastModified().toEpochMilli())
                    .directory(false)
                    .url(getUrl(objectKey))
                    .eTag(response.eTag())
                    .storageClass(response.storageClass() != null ? response.storageClass().toString() : null)
                    .build();
        } catch (NoSuchKeyException e) {
            log.info("文件不存在: {}", objectKey);
            return null;
        } catch (Exception e) {
            log.error("获取文件信息失败: {}", objectKey, e);
            return null;
        }
    }

    @Override
    public List<CloudStorageFileInfo> list(String prefix) {
        try {
            List<CloudStorageFileInfo> result = new ArrayList<>();
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(config.getBucketName())
                    .prefix(prefix != null ? prefix : "")
                    .build();

            ListObjectsV2Iterable responses = s3Client.listObjectsV2Paginator(request);
            for (ListObjectsV2Response response : responses) {
                for (S3Object s3Object : response.contents()) {
                    String key = s3Object.key();
                    if (key.equals(prefix)) {
                        continue;
                    }
                    result.add(buildFileInfo(s3Object));
                }
            }
            log.info("列出对象成功, prefix={}, 数量={}", prefix, result.size());
            return result;
        } catch (Exception e) {
            log.error("列出对象失败, prefix={}", prefix, e);
            throw new RuntimeException("S3列出对象失败", e);
        }
    }

    @Override
    public List<CloudStorageFileInfo> listDirect(String prefix) {
        try {
            List<CloudStorageFileInfo> result = new ArrayList<>();
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(config.getBucketName())
                    .prefix(prefix != null ? prefix : "")
                    .delimiter("/")
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            for (S3Object s3Object : response.contents()) {
                String key = s3Object.key();
                if (key.equals(prefix)) {
                    continue;
                }
                result.add(buildFileInfo(s3Object));
            }

            for (CommonPrefix commonPrefix : response.commonPrefixes()) {
                String dirKey = commonPrefix.prefix();
                result.add(CloudStorageFileInfo.builder()
                        .objectKey(dirKey)
                        .fileName(extractFileName(dirKey.substring(0, dirKey.length() - 1)))
                        .size(0)
                        .directory(true)
                        .url(getUrl(dirKey))
                        .build());
            }

            log.info("列出直接子项成功, prefix={}, 数量={}", prefix, result.size());
            return result;
        } catch (Exception e) {
            log.error("列出直接子项失败, prefix={}", prefix, e);
            throw new RuntimeException("S3列出直接子项失败", e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("判断文件是否存在失败: {}", objectKey, e);
            throw new RuntimeException("S3判断文件是否存在失败", e);
        }
    }

    @Override
    public String getUrl(String objectKey) {
        String baseEndpoint = config.getEndpoint() != null ? config.getEndpoint() : "https://s3." + config.getRegion() + ".amazonaws.com";
        if (config.isPathStyleAccess()) {
            return baseEndpoint + "/" + config.getBucketName() + "/" + objectKey;
        }
        return baseEndpoint.replace("://", "://" + config.getBucketName() + ".") + "/" + objectKey;
    }

    @Override
    public void close() {
        if (s3Client != null) {
            s3Client.close();
            log.info("S3客户端已关闭");
        }
    }

    private CloudStorageFileInfo buildFileInfo(S3Object s3Object) {
        return CloudStorageFileInfo.builder()
                .objectKey(s3Object.key())
                .fileName(extractFileName(s3Object.key()))
                .size(s3Object.size())
                .lastModified(s3Object.lastModified().toEpochMilli())
                .directory(false)
                .url(getUrl(s3Object.key()))
                .eTag(s3Object.eTag())
                .storageClass(s3Object.storageClass() != null ? s3Object.storageClass().toString() : null)
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