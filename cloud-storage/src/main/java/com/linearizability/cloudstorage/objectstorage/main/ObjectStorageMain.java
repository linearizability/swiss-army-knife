package com.linearizability.cloudstorage.objectstorage.main;

import com.linearizability.cloudstorage.objectstorage.CloudStorageClient;
import com.linearizability.cloudstorage.objectstorage.CloudStorageClientFactory;
import com.linearizability.cloudstorage.objectstorage.CloudStorageFileInfo;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 对象存储工具示例入口
 *
 * @author ZhangBoyuan
 * @since 2026-06-02
 */
@Slf4j
public class ObjectStorageMain {

    /**
     * 云存储客户端
     */
    private static final CloudStorageClient CLIENT = CloudStorageClientFactory.create();

    static void main() {
        try {
//            upload("demo/demo.zip", "D:/demo.zip");
//            listDirect("demo");
//            getFileInfo("demo/demo.zip");
//            download("demo/demo.zip", "D:/demo.zip");
        } finally {
            CloudStorageClientFactory.closeAll();
        }
    }

    /**
     * 获取文件信息
     *
     * @param objectKey 文件对象键
     */
    private static void getFileInfo(String objectKey) {
        log.info("========== 获取文件信息 ==========");
        CloudStorageFileInfo info = CLIENT.getFileInfo(objectKey);
        log.info("文件信息: {}", info);
    }

    /**
     * 删除文件
     *
     * @param objectKey 文件对象键
     */
    private static void delete(String objectKey) {
        log.info("========== 删除文件 ==========");
        CLIENT.delete(objectKey);
    }

    /**
     * 下载文件
     *
     * @param objectKey     文件对象键
     * @param localSavePath 本地存储路径
     */
    private static void download(String objectKey, String localSavePath) {
        try {
            log.info("========== 下载文件 ==========");
            CLIENT.download(objectKey, localSavePath);
            long size = Files.size(Path.of(localSavePath));
            log.info("下载成功, 文件大小: {} bytes", size);
        } catch (Exception e) {
            log.error("下载失败", e);
        }
    }

    private static void upload(String objectKey, String localFilePath) {
        log.info("========== 上传文件 ==========");
        try {
            String url = CLIENT.upload(objectKey, localFilePath);
            log.info("上传成功, URL: {}", url);
        } catch (Exception e) {
            log.error("上传失败", e);
        }
    }

    private static void listDirect(String prefix) {
        log.info("========== 列出目录层级 ==========");
        List<CloudStorageFileInfo> rootItems = CLIENT.listDirect(prefix);
        log.info("根目录内容:");
        rootItems.forEach(f -> log.info("  {}", f));

        for (CloudStorageFileInfo item : rootItems) {
            if (item.isDirectory()) {
                List<CloudStorageFileInfo> subItems = CLIENT.listDirect(item.getObjectKey());
                log.info("子目录 {} 内容:", item.getObjectKey());
                subItems.forEach(f -> log.info("    {}", f));
            }
        }
    }
}