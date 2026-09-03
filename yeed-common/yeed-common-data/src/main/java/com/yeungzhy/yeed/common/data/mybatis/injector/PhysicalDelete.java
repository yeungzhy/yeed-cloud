package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 注入 {@code physicalDelete}：根据 Wrapper 条件物理删除，绕过 {@code @TableLogic} 过滤
 *
 * <p>WHERE 只取 {@code ew.sqlSegment}，不复用 {@link #sqlWhereEntityWrapper}——
 * 后者会强制追加“未删除”条件，那样就永远清不掉已删数据；
 * Wrapper 上设置的 entity 条件同样不生效，条件须全部通过 Wrapper 方法构建
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see CustomSqlInjector
 * @see BaseMapper#physicalDelete
 */
public class PhysicalDelete extends AbstractMethod {

    /**
     * WHERE 条件脚本：只取 Wrapper 自身条件（不带逻辑删除过滤），条件为空时兜底 1=0 防止全表物理删除
     */
    private static final String SQL_WHERE_WRAPPER = "<choose>"
            + "<when test=\"ew != null and ew.sqlSegment != null and ew.sqlSegment != ''\"> WHERE ${ew.sqlSegment}</when>"
            + "<otherwise> WHERE 1=0</otherwise>"
            + "</choose>";

    /**
     * 物理删除模板
     * <p>占位符依次为表名、WHERE 条件、sqlComment()
     */
    private static final String SQL_PHYSICAL_DELETE = "<script>DELETE FROM %s %s %s</script>";

    /**
     * 声明注入的方法名
     * <p>字符串必须与 Mapper 上声明的方法名逐字一致，否则调用方抛 {@code BindingException}：
     * <pre>{@code
     * int physicalDelete(@Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);
     * }</pre>
     */
    public PhysicalDelete() {
        super("physicalDelete");
    }

    /**
     * {@inheritDoc}
     * <p>全表可用，无注入前置条件
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_PHYSICAL_DELETE, tableInfo.getTableName(), SQL_WHERE_WRAPPER, sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
        return addDeleteMappedStatement(mapperClass, methodName, sqlSource);
    }

}
