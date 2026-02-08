package com.linearizability.database;

import com.linearizability.properties.PropertiesUtil;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * 数据库相关工具类
 *
 * @author ZhangBoyuan
 * @since 2026-02-07
 */
@Slf4j
public class DatabaseUtil {

    /**
     * 单例数据源实例
     */
    private static volatile HikariDataSource DATA_SOURCE;

    /**
     * 双重检查锁定获取数据源
     */
    public static HikariDataSource getDataSource() {
        if (Objects.isNull(DATA_SOURCE)) {
            synchronized (DatabaseUtil.class) {
                if (Objects.isNull(DATA_SOURCE)) {
                    try {
                        Properties props = PropertiesUtil.loadDbProperties();
                        DATA_SOURCE = new HikariDataSource();
                        DATA_SOURCE.setJdbcUrl(props.getProperty("db.url"));
                        DATA_SOURCE.setUsername(props.getProperty("db.username"));
                        DATA_SOURCE.setPassword(props.getProperty("db.password"));
                        DATA_SOURCE.setDriverClassName(props.getProperty("db.driver"));
                        // 连接池中最大的连接数
                        DATA_SOURCE.setMaximumPoolSize(10);
                        // 连接池中保持的最小空闲连接数
                        DATA_SOURCE.setMinimumIdle(2);
                        // 获取连接的超时时间（单位毫秒）
                        DATA_SOURCE.setConnectionTimeout(30000);
                        // 连接最大空闲时间（单位毫秒）
                        DATA_SOURCE.setIdleTimeout(600000);
                        // 连接最大生存时间（单位毫秒）
                        DATA_SOURCE.setMaxLifetime(1800000);
                        log.info("数据库连接池初始化成功");
                    } catch (Exception e) {
                        log.error("数据库连接池初始化失败", e);
                        throw new RuntimeException("数据库连接池初始化失败", e);
                    }
                }
            }
        }
        return DATA_SOURCE;
    }

    /**
     * 获取JdbcTemplate实例
     */
    public static JdbcTemplate getJdbcTemplate() {
        return new JdbcTemplate(getDataSource());
    }

    /**
     * 获取连接池状态信息
     */
    public static void getPoolStatus() {
        if (Objects.isNull(DATA_SOURCE)) {
            log.info("连接池未初始化");
        }

        log.info("数据库连接池状态：活跃连接数-{}, 空闲连接数-{}, 总连接数-{}",
                DATA_SOURCE.getHikariPoolMXBean().getActiveConnections(),
                DATA_SOURCE.getHikariPoolMXBean().getIdleConnections(),
                DATA_SOURCE.getHikariPoolMXBean().getTotalConnections()
        );
    }

    /**
     * 关闭数据源
     */
    public static void closeDataSource() {
        if (Objects.nonNull(DATA_SOURCE) && !DATA_SOURCE.isClosed()) {
            try {
                DATA_SOURCE.close();
                log.info("数据库连接池已关闭");
            } catch (Exception e) {
                log.error("关闭数据库连接池失败", e);
            } finally {
                DATA_SOURCE = null;
            }
        }
    }

    /**
     * 执行查询并返回结果列表
     */
    public static <T> List<T> getList(String sql, Class<T> tClass) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate();
            log.info("执行SQL查询: {}", sql);
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(tClass));
        } catch (Exception e) {
            log.error("执行SQL查询失败: {}", sql, e);
            throw new RuntimeException("数据库查询失败", e);
        }
    }

    /**
     * 执行查询并返回单个结果
     */
    public static <T> T getOne(String sql, Class<T> tClass) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate();
            log.info("执行SQL查询(单条): {}", sql);
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(tClass));
        } catch (Exception e) {
            log.error("执行SQL查询失败: {}", sql, e);
            throw new RuntimeException("数据库查询失败", e);
        }
    }

    /**
     * 执行更新操作
     */
    public static int update(String sql) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate();
            log.info("执行SQL更新: {}", sql);
            return jdbcTemplate.update(sql);
        } catch (Exception e) {
            log.error("执行SQL更新失败: {}", sql, e);
            throw new RuntimeException("数据库更新失败", e);
        }
    }

}
