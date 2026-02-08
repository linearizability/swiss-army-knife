# swiss-army-knife

> 编程过程中常用的一些工具集合，基于 JDK 25

# 使用建议

建议项目拉取后执行以下命令，避免误操作将私密参数提交至云端

~~~bash
-- 忽略 db-generator.properties 文件后续修改（仅本地）
git update-index --assume-unchanged common/src/main/resources/db.properties

-- 查看被忽略后续修改的文件
git ls-files -v | grep '^[[:lower:]]'

-- 取消忽略标记
git update-index --no-assume-unchanged common/src/main/resources/db.properties
~~~