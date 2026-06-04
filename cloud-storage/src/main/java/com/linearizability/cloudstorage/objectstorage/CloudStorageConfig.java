package com.linearizability.cloudstorage.objectstorage;

import com.linearizability.properties.PropertiesUtil;
import lombok.Data;

import java.util.Properties;

/**
 * 云存储配置
 *
 * @author ZhangBoyuan
 * @since 2026-06-02
 */
@Data
public class CloudStorageConfig {

    /**
     * 厂商类型：ali / s3
     */
    private String provider;

    /**
     * 访问端点
     * <p>阿里云OSS示例：https://oss-cn-hangzhou.aliyuncs.com
     * <p>AWS S3示例：https://s3.amazonaws.com
     * <p>MinIO示例：http://127.0.0.1:9000
     */
    private String endpoint;

    /**
     * 访问密钥ID
     */
    private String accessKeyId;

    /**
     * 访问密钥Secret
     */
    private String accessKeySecret;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 区域（AWS S3需要，如us-east-1；阿里云OSS可从endpoint推断）
     */
    private String region;

    /**
     * 路径风格访问（MinIO需要设为true，AWS S3默认false）
     */
    private boolean pathStyleAccess;

    /**
     * 从配置文件加载云存储配置
     */
    public static CloudStorageConfig load() {
        return load(null);
    }

    /**
     * 从指定前缀的配置文件加载云存储配置
     *
     * @param prefix 配置前缀，如"cloud-storage.ali"或"cloud-storage.minio"，传null则使用默认前缀"cloud-storage"
     */
    public static CloudStorageConfig load(String prefix) {
        Properties props = PropertiesUtil.loadCloudStorageProperties();
        String p = (prefix == null || prefix.isEmpty()) ? "cloud-storage" : prefix;

        CloudStorageConfig config = new CloudStorageConfig();
        config.setProvider(props.getProperty(p + ".provider"));
        config.setEndpoint(props.getProperty(p + ".endpoint"));
        config.setAccessKeyId(props.getProperty(p + ".accessKeyId"));
        config.setAccessKeySecret(props.getProperty(p + ".accessKeySecret"));
        config.setBucketName(props.getProperty(p + ".bucketName"));
        config.setRegion(props.getProperty(p + ".region"));
        config.setPathStyleAccess(Boolean.parseBoolean(props.getProperty(p + ".pathStyleAccess", "false")));

        return config;
    }

    @Override
    public String toString() {
        return "CloudStorageConfig{provider='%s', endpoint='%s', bucketName='%s', region='%s', pathStyleAccess=%s}"
                .formatted(provider, endpoint, bucketName, region, pathStyleAccess);
    }
}