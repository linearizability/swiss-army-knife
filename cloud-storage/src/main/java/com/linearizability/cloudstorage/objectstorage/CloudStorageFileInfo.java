package com.linearizability.cloudstorage.objectstorage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 云存储文件信息
 *
 * @author ZhangBoyuan
 * @since 2026-06-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudStorageFileInfo {

    /**
     * 对象键（文件在存储桶中的完整路径）
     */
    private String objectKey;

    /**
     * 文件名（对象键的最后一部分）
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private long size;

    /**
     * 最后修改时间（epoch毫秒）
     */
    private long lastModified;

    /**
     * 是否为目录
     */
    private boolean directory;

    /**
     * 文件的访问URL
     */
    private String url;

    /**
     * ETag（如适用）
     */
    private String eTag;

    /**
     * 存储类型（如STANDARD、GLACIER等）
     */
    private String storageClass;

    @Override
    public String toString() {
        String displayKey = directory ? objectKey + "/" : objectKey;
        String sizeDisplay = directory ? "-" : formatSize(size);
        return "%-60s %10s  %s".formatted(displayKey, sizeDisplay, directory ? "[DIR]" : "");
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }
}