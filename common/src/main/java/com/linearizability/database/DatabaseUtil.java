package com.linearizability.database;

import com.linearizability.properties.PropertiesUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Properties;

/**
 * 数据库相关工具类
 *
 * @author ZhangBoyuan
 * @since 2026-02-07
 */
public class DatabaseUtil {

    /**
     * Properties实例，用于加载配置
     */
    private static final Properties props = PropertiesUtil.loadDbProperties();

    /**
     * 数据库连接信息
     *
     */
    private static final String DB_URL = props.getProperty("db.url");

    /**
     * 数据库用户名
     */
    private static final String DB_USERNAME = props.getProperty("db.username");

    /**
     * 数据库密码
     */
    private static final String DB_PASSWORD = props.getProperty("db.password");

    /**
     * 数据库驱动
     */
    private static final String DB_DRIVER = props.getProperty("db.driver");

    public static HikariDataSource getDataSource() {
        HikariDataSource result = new HikariDataSource();
        result.setJdbcUrl(DB_URL);
        result.setUsername(DB_USERNAME);
        result.setPassword(DB_PASSWORD);
        result.setDriverClassName(DB_DRIVER);
        result.setMaximumPoolSize(5);
        return result;
    }

    public static JdbcTemplate getJdbcTemplate(HikariDataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    public static <T> List<T> getList(String sql, Class<T> tClass) {
        HikariDataSource dataSource = getDataSource();
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSource);
        List<T> result = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(tClass));
        dataSource.close();
        return result;
    }

}
