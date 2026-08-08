package com.yeungzhy.yeed.common.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 自定义 SQL 方法：根据 Wrapper 条件物理删除, 忽略逻辑删除过滤（physicalDelete）
 *
 * <p> 详述: 继承 {@link AbstractMethod} 实现 SQL 注入, 生成真正的 DELETE 语句,
 * WHERE 只取 Wrapper 自身条件, 不追加 delete_time 未删除条件, 用于按条件批量彻底清除;
 * 配合 {@link CustomSqlInjector} 注册后, 所有 Mapper 均可声明方法调用（方法签名示例见类注释末尾）
 *
 * <p> 生成的 SQL 形如: {@code DELETE FROM yeed_sys_user WHERE (username = ?)}
 *
 * <p> 注意: WHERE 条件不复用 {@code sqlWhereEntityWrapper}（其会强制追加逻辑删除条件）, 只取 {@code ew.sqlSegment};
 * 为防止误删全表, Wrapper 未携带有效条件时兜底为 {@code WHERE 1=0}（一条都不删）;
 * 另外 Wrapper 上设置的 entity 条件不会生效, 条件请全部通过 Wrapper 方法构建;
 *
 * <p> 调用方声明:
 * <pre>
 *     {@code int physicalDelete(@Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);}
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public class PhysicalDelete extends AbstractMethod {

    /**
     * WHERE 条件脚本：取 Wrapper 条件（不带逻辑删除过滤）, 条件为空时兜底 1=0 防止全表物理删除
     */
    private static final String SQL_WHERE_WRAPPER = "<choose>"
            + "<when test=\"ew != null and ew.sqlSegment != null and ew.sqlSegment != ''\"> WHERE ${ew.sqlSegment}</when>"
            + "<otherwise> WHERE 1=0</otherwise>"
            + "</choose>";

    /**
     * 物理删除 SQL 模板
     * <p> 占位符依次为: 表名、WHERE 条件、sqlComment()
     */
    private static final String SQL_PHYSICAL_DELETE = "<script>DELETE FROM %s %s %s</script>";

    public PhysicalDelete() {
        super("physicalDelete");
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_PHYSICAL_DELETE, tableInfo.getTableName(), SQL_WHERE_WRAPPER, sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
        return addDeleteMappedStatement(mapperClass, methodName, sqlSource);
    }

}
