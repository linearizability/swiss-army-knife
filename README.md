# swiss-army-knife

> 编程过程中常用的一些工具集合，基于 JDK 25

# 模块说明

| 模块 | 说明 |
|------|------|
| common | 基础设施：配置加载、数据库连接池 |
| net | 网络工具：UDP广播、HTTP服务器、文件传输 |
| database | 数据库工具：MyBatis-Plus代码生成 |
| document | 文档生成：Excel省市区联动模板 |
| cloud-storage | 云存储：对接阿里云OSS、AWS S3、MinIO |

# 使用建议

建议项目拉取后执行以下命令，避免误操作将私密参数提交至云端

~~~ bash
-- 忽略 db.properties 文件后续修改（仅本地）
git update-index --assume-unchanged common/src/main/resources/db.properties

-- 忽略 cloud-storage.properties 文件后续修改（仅本地）
git update-index --assume-unchanged common/src/main/resources/cloud-storage.properties

-- 查看被忽略后续修改的文件
git ls-files -v | grep '^[[:lower:]]'

-- 取消忽略标记
git update-index --no-assume-unchanged common/src/main/resources/db.properties
git update-index --no-assume-unchanged common/src/main/resources/cloud-storage.properties
~~~

# 常用命令

~~~ bash
-- 删除所有 log 文件
find . -type f -name "*.log" -delete
~~~