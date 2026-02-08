package com.linearizability.mybatisplus.utils;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.InjectionConfig;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.linearizability.properties.PropertiesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * 数据库逆向工程工具类
 * 基于MyBatis-Plus Generator自动生成Entity、Mapper、Service等代码
 *
 * @author ZhangBoyuan
 * @since 2025-10-20
 */
public class DbGenerator {

    /**
     * Properties实例，用于加载配置
     */
    private static final Properties DB_PROPERTIES = PropertiesUtil.loadDbProperties();

    private static final Properties props = PropertiesUtil.load(DbGenerator.class, "db-generator.properties");

    /**
     * 数据库连接URL
     */
    private static final String JDBC_URL = DB_PROPERTIES.getProperty("db.url");

    /**
     * 数据库用户名
     */
    private static final String DB_USER_NAME = DB_PROPERTIES.getProperty("db.username");

    /**
     * 数据库密码
     */
    private static final String DB_PASSWORD = DB_PROPERTIES.getProperty("db.password");

    /**
     * 数据库驱动
     */
    private static final String DB_DRIVER = DB_PROPERTIES.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");

    /**
     * 生成代码的包名
     */
    private static final String PACKAGE_NAME = props.getProperty("package.name");

    /**
     * 模块名称，没有可修改为空字符串
     */
    private static final String MODULE_NAME = props.getProperty("module.name");

    /**
     * Java源码输出路径
     */
    private static final String SOURCE_PATH = props.getProperty("source.path");

    /**
     * Mapper XML文件输出路径
     */
    private static final String MAPPER_XML_PATH = props.getProperty("mapper.xml.path");

    /**
     * 数据库表前缀，没有可修改为空字符串
     */
    private static final String TABLE_PREFIX = props.getProperty("table.prefix");

    /**
     * 实体类父类，没有可修改为空字符串
     */
    private static final String SUPER_ENTITY_CLASS = props.getProperty("super.entity.class");

    /**
     * 代码作者
     */
    private static final String AUTHOR = props.getProperty("author");

    /**
     * 实体类命名格式
     */
    private static final String ENTITY_NAME_FORMAT = props.getProperty("entity.name.format");

    /**
     * Service接口命名格式
     */
    private static final String SERVICE_NAME_FORMAT = props.getProperty("service.name.format");

    /**
     * Service实现类命名格式
     */
    private static final String SERVICE_IMPL_NAME_FORMAT = props.getProperty("service.impl.name.format");

    /**
     * Mapper XML模板路径
     */
    private static final String MAPPER_TEMPLATE_PATH = props.getProperty("mapper.template.path");

    /**
     * Service包名
     */
    private static final String SERVICE_PACKAGE_NAME = props.getProperty("service.package.name");

    /**
     * Service实现类包名
     */
    private static final String SERVICE_IMPL_PACKAGE_NAME = props.getProperty("service.impl.package.name");

    /**
     * 父类中已存在的字段（需填写数据库字段而非类字段），生成时会排除这些字段；没有可修改为空列表
     */
    private static final String[] SUPER_ENTITY_COLUMNS = parsePropertyArray(props.getProperty("super.entity.columns"));

    /**
     * 需要生成代码的数据库表名
     */
    private static final String[] TABLE_NAMES = parsePropertyArray(props.getProperty("table.names"));

    /**
     * 解析逗号分隔的属性值为数组
     */
    private static String[] parsePropertyArray(String propertyValue) {
        if (Objects.isNull(propertyValue) || propertyValue.trim().isEmpty()) {
            return new String[]{};
        }
        String[] values = propertyValue.split(",");
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
        }
        return values;
    }

    /**
     * 构建全局配置
     *
     * @param projectPath 项目路径
     * @return 全局配置对象
     */
    private static GlobalConfig buildGlobalConfig(String projectPath) {
        GlobalConfig config = new GlobalConfig();
        return config.setOutputDir(projectPath + SOURCE_PATH)
                .setAuthor(AUTHOR)
                .setEnableCache(false)
                .setFileOverride(true)
                .setBaseResultMap(true)
                .setOpen(false)
                .setServiceName(SERVICE_NAME_FORMAT)
                .setServiceImplName(SERVICE_IMPL_NAME_FORMAT)
                .setEntityName(ENTITY_NAME_FORMAT);
    }

    /**
     * 构建数据源配置
     *
     * @return 数据源配置对象
     */
    private static DataSourceConfig buildDataSourceConfig() {
        DataSourceConfig config = new DataSourceConfig();
        return config.setUrl(JDBC_URL)
                .setDriverName(DB_DRIVER)
                .setUsername(DB_USER_NAME)
                .setPassword(DB_PASSWORD);
    }

    /**
     * 构建包配置
     *
     * @return 包配置对象
     */
    private static PackageConfig buildPackageConfig() {
        PackageConfig config = new PackageConfig();
        return config.setModuleName(MODULE_NAME)
                .setParent(PACKAGE_NAME)
                .setService(SERVICE_PACKAGE_NAME)
                .setServiceImpl(SERVICE_IMPL_PACKAGE_NAME);
    }

    /**
     * 构建自定义配置
     *
     * @param projectPath 项目路径
     * @return 自定义配置对象
     */
    private static InjectionConfig buildInjectionConfig(String projectPath) {
        InjectionConfig config = new InjectionConfig() {
            @Override
            public void initMap() {
                // 无需初始化
            }
        };

        List<FileOutConfig> fileOutConfigs = new ArrayList<>();
        fileOutConfigs.add(new FileOutConfig(MAPPER_TEMPLATE_PATH) {
            @Override
            public String outputFile(TableInfo tableInfo) {
                return projectPath + MAPPER_XML_PATH + tableInfo.getEntityName() + "Mapper" + StringPool.DOT_XML;
            }
        });

        config.setFileOutConfigList(fileOutConfigs);
        return config;
    }

    /**
     * 构建策略配置
     *
     * @return 策略配置对象
     */
    private static StrategyConfig buildStrategyConfig() {
        StrategyConfig config = new StrategyConfig();
        return config.setNaming(NamingStrategy.underline_to_camel)
                .setColumnNaming(NamingStrategy.underline_to_camel)
                .setEntityLombokModel(true)
                .setTablePrefix(TABLE_PREFIX)
                .setSuperEntityClass(SUPER_ENTITY_CLASS)
                .setSuperEntityColumns(SUPER_ENTITY_COLUMNS)
                .setInclude(TABLE_NAMES);
    }

    /**
     * 主方法，执行代码生成
     */
    static void main() {
        AutoGenerator generator = new AutoGenerator();
        String projectPath = System.getProperty("user.dir");

        generator.setGlobalConfig(buildGlobalConfig(projectPath));
        generator.setDataSource(buildDataSourceConfig());
        generator.setPackageInfo(buildPackageConfig());
        generator.setCfg(buildInjectionConfig(projectPath));
        generator.setStrategy(buildStrategyConfig());
        generator.setTemplateEngine(new FreemarkerTemplateEngine());
        generator.setTemplate(new TemplateConfig().setXml(null).setController(null));

        generator.execute();
    }

}