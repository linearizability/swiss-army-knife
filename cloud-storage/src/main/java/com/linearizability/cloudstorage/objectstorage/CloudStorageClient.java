package com.linearizability.cloudstorage.objectstorage;

import java.io.InputStream;
import java.util.List;

/**
 * 云存储统一接口，不同厂商的实现类遵循该接口
 *
 * @author ZhangBoyuan
 * @since 2026-06-02
 */
public interface CloudStorageClient extends AutoCloseable {

    /**
     * 上传文件（通过输入流）
     *
     * @param objectKey     对象键（即文件在存储桶中的路径）
     * @param stream         文件输入流
     * @param contentLength  文件内容长度，-1表示未知
     * @return 文件的访问URL
     */
    String upload(String objectKey, InputStream stream, long contentLength);

    /**
     * 上传文件（通过本地文件路径）
     *
     * @param objectKey 对象键
     * @param filePath  本地文件路径
     * @return 文件的访问URL
     */
    String upload(String objectKey, String filePath);

    /**
     * 下载文件到输入流
     *
     * @param objectKey 对象键
     * @return 文件内容的输入流，调用方负责关闭
     */
    InputStream download(String objectKey);

    /**
     * 下载文件到本地路径
     *
     * @param objectKey 对象键
     * @param localPath 本地保存路径
     */
    void download(String objectKey, String localPath);

    /**
     * 删除文件
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);

    /**
     * 获取文件信息
     *
     * @param objectKey 对象键
     * @return 文件信息，不存在时返回null
     */
    CloudStorageFileInfo getFileInfo(String objectKey);

    /**
     * 列出指定前缀下的所有对象
     *
     * @param prefix 对象键前缀（目录路径），传空字符串表示列出根目录
     * @return 文件信息列表
     */
    List<CloudStorageFileInfo> list(String prefix);

    /**
     * 列出指定前缀下的直接子项（不递归），模拟目录层级浏览
     *
     * @param prefix 对象键前缀（目录路径），传空字符串表示列出根目录
     * @return 直接子项列表（包含子目录和文件）
     */
    List<CloudStorageFileInfo> listDirect(String prefix);

    /**
     * 判断文件是否存在
     *
     * @param objectKey 对象键
     * @return 是否存在
     */
    boolean exists(String objectKey);

    /**
     * 获取文件的访问URL
     *
     * @param objectKey 对象键
     * @return 访问URL
     */
    String getUrl(String objectKey);

    /**
     * 关闭客户端并释放资源
     */
    @Override
    void close();
}