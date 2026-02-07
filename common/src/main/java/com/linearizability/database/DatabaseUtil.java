package com.linearizability.database;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * @author ZhangBoyuan
 * @since 2026-02-07
 */
public class DatabaseUtil {

    public static <T> List<T> getList(HikariDataSource dataSource, String sql, Class<T> tClass) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<T> result = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(tClass));
        dataSource.close();
        return result;
    }

}
