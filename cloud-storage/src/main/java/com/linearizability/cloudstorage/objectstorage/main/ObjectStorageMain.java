package com.linearizability.cloudstorage.objectstorage.main;

import com.linearizability.cloudstorage.objectstorage.CloudStorageClient;
import com.linearizability.cloudstorage.objectstorage.CloudStorageClientFactory;
import com.linearizability.cloudstorage.objectstorage.CloudStorageFileInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
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

    static void main() {
        CloudStorageClient client = CloudStorageClientFactory.create();

        try {
            list(client, "");
            demoUploadAndDownload(client);
            demoFileInfoAndExists(client);
            listDirect(client, "");
        } finally {
            CloudStorageClientFactory.closeAll();
        }
    }

    private static void list(CloudStorageClient client, String prefix) {
        log.info("========== 列出所有对象 ==========");
        List<CloudStorageFileInfo> allFiles = client.list(prefix);
        allFiles.forEach(f -> log.info("  {}", f));
        log.info("总对象数: {}", allFiles.size());
    }

    private static void demoUploadAndDownload(CloudStorageClient client) {
        log.info("========== 上传文件 ==========");
        String objectKey = "demo/test-upload.txt";
        String localFilePath = "demo-upload.txt";

        try {
            Path tempFile = Path.of(localFilePath);
            Files.writeString(tempFile, "Hello Cloud Storage! 你好云存储！");

            String url = client.upload(objectKey, localFilePath);
            log.info("上传成功, URL: {}", url);

            log.info("========== 下载文件 ==========");
            String downloadPath = "demo-download.txt";
            client.download(objectKey, downloadPath);
            String content = Files.readString(Path.of(downloadPath));
            log.info("下载文件内容: {}", content);

            log.info("========== 流式上传 ==========");
            try (InputStream stream = Files.newInputStream(tempFile)) {
                String streamUrl = client.upload("demo/stream-upload.txt", stream, Files.size(tempFile));
                log.info("流式上传成功, URL: {}", streamUrl);
            }

            Files.deleteIfExists(Path.of(downloadPath));
            Files.deleteIfExists(tempFile);
        } catch (Exception e) {
            log.error("上传下载演示失败", e);
        }
    }

    private static void demoFileInfoAndExists(CloudStorageClient client) {
        log.info("========== 文件信息与存在判断 ==========");
        String objectKey = "demo/test-upload.txt";

        boolean exists = client.exists(objectKey);
        log.info("文件 {} 是否存在: {}", objectKey, exists);

        if (exists) {
            CloudStorageFileInfo info = client.getFileInfo(objectKey);
            if (info != null) {
                log.info("文件信息: objectKey={}, size={}, lastModified={}, eTag={}",
                        info.getObjectKey(), info.getSize(), info.getLastModified(), info.getETag());
            }
        }

        log.info("========== 删除文件 ==========");
        try {
            client.delete(objectKey);
            log.info("删除成功: {}", objectKey);
        } catch (Exception e) {
            log.error("删除失败", e);
        }
    }

    private static void listDirect(CloudStorageClient client, String prefix) {
        log.info("========== 列出目录层级 ==========");
        List<CloudStorageFileInfo> rootItems = client.listDirect(prefix);
        log.info("根目录内容:");
        rootItems.forEach(f -> log.info("  {}", f));

        for (CloudStorageFileInfo item : rootItems) {
            if (item.isDirectory()) {
                List<CloudStorageFileInfo> subItems = client.listDirect(item.getObjectKey());
                log.info("子目录 {} 内容:", item.getObjectKey());
                subItems.forEach(f -> log.info("    {}", f));
            }
        }
    }
}