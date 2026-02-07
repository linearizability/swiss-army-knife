package com.linearizability.properties;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * properties 工具类
 *
 * @author ZhangBoyuan
 * @since 2026-02-07
 */
@Slf4j
public class PropertiesUtil {

    /**
     * 加载配置文件
     */
    public static <T> Properties load(Class<T> clazz, String fileName) {
        log.info("开始加载 {} 配置文件", fileName);
        Properties properties = new Properties();
        try (InputStream input = clazz.getClassLoader().getResourceAsStream(fileName)) {
            if (Objects.isNull(input)) {
                log.error("无法找到 {} 配置文件", fileName);
                throw new RuntimeException("无法找到 %s 配置文件".formatted(fileName));
            }
            properties.load(input);
        } catch (IOException e) {
            log.error("加载配置文件失败");
            throw new RuntimeException("加载配置文件失败", e);
        }
        return properties;
    }

}
