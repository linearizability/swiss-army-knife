package com.linearizability.cloudstorage.objectstorage;

import com.linearizability.cloudstorage.objectstorage.ali.AliCloudStorageClient;
import com.linearizability.cloudstorage.objectstorage.s3.S3CloudStorageClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云存储客户端工厂，根据配置创建对应的客户端实例
 * <p>支持通过指定配置前缀创建多个不同厂商的客户端
 *
 * @author ZhangBoyuan
 * @since 2026-06-02
 */
@Slf4j
public class CloudStorageClientFactory {

    private static final Map<String, CloudStorageClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * 使用默认配置（cloud-storage.前缀）创建云存储客户端
     */
    public static CloudStorageClient create() {
        return createByPrefix("cloud-storage");
    }

    /**
     * 使用指定配置前缀创建云存储客户端
     * <p>例如传入"cloud-storage.ali"则从cloud-storage.properties中读取cloud-storage.ali.provider等配置
     *
     * @param configPrefix 配置前缀
     * @return 对应厂商的云存储客户端实例
     */
    public static CloudStorageClient createByPrefix(String configPrefix) {
        String cacheKey = (configPrefix == null) ? "cloud-storage" : configPrefix;

        return CLIENT_CACHE.computeIfAbsent(cacheKey, key -> {
            CloudStorageConfig config = CloudStorageConfig.load(key);
            log.info("创建云存储客户端, 配置前缀={}, provider={}", key, config.getProvider());
            return createClient(config);
        });
    }

    /**
     * 直接通过配置对象创建云存储客户端（不缓存）
     *
     * @param config 云存储配置
     * @return 对应厂商的云存储客户端实例
     */
    public static CloudStorageClient create(CloudStorageConfig config) {
        return createClient(config);
    }

    /**
     * 关闭指定配置前缀的缓存客户端
     */
    public static void close(String configPrefix) {
        String cacheKey = (configPrefix == null) ? "cloud-storage" : configPrefix;
        CloudStorageClient client = CLIENT_CACHE.remove(cacheKey);
        if (client != null) {
            client.close();
        }
    }

    /**
     * 关闭所有缓存客户端
     */
    public static void closeAll() {
        CLIENT_CACHE.forEach((key, client) -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭云存储客户端失败, key={}", key, e);
            }
        });
        CLIENT_CACHE.clear();
        log.info("所有云存储客户端已关闭");
    }

    private static CloudStorageClient createClient(CloudStorageConfig config) {
        String provider = config.getProvider();
        if (provider == null || provider.isEmpty()) {
            throw new IllegalArgumentException("云存储配置中provider不能为空，可选值: ali, s3");
        }

        return switch (provider.toLowerCase()) {
            case "ali" -> new AliCloudStorageClient(config);
            case "s3" -> new S3CloudStorageClient(config);
            default -> throw new IllegalArgumentException("不支持的云存储provider: " + provider + "，可选值: ali, s3");
        };
    }
}