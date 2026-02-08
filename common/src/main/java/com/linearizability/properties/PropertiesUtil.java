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
     * 数据库配置文件
     */
    private static final String DB_PROPERTIES = "db.properties";

    /**
     * 加载数据库配置文件
     */
    public static Properties loadDbProperties() {
        return load(PropertiesUtil.class, DB_PROPERTIES);
    }

    /**
     * 加载配置文件（通过指定类的ClassLoader）
     */
    public static Properties load(Class<?> clazz, String fileName) {
        return loadWithClass(clazz, fileName);
    }

    /**
     * 核心加载逻辑
     */
    private static Properties loadWithClass(Class<?> clazz, String fileName) {
        log.info("开始加载 {} 配置文件", fileName);
        Properties properties = new Properties();
        try (InputStream input = clazz.getClassLoader().getResourceAsStream(fileName)) {
            if (Objects.isNull(input)) {
                String errorMsg = "无法找到配置文件: %s (通过类 %s 加载)".formatted(fileName, clazz.getSimpleName());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            properties.load(input);
            log.info("成功加载配置文件: {}", fileName);
        } catch (IOException e) {
            String errorMsg = "读取配置文件失败: %s (通过类 %s 加载)".formatted(fileName, clazz.getSimpleName());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
        return properties;
    }

}
