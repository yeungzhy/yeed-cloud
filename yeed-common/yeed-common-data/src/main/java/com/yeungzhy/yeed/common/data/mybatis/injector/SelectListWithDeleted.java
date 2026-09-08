package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 注入 {@code selectListWithDeleted}：根据 Wrapper 条件查询列表，包含已逻辑删除的数据
 *
 * <p> WHERE 只取 {@code ew.sqlSegment}，不复用 {@link #sqlWhereEntityWrapper}：后者会强制追加“未删除”条件，
 * 那样就永远查不到已删数据。
 * Wrapper 上设置的 entity 条件同样不生效，条件须全部通过 Wrapper 方法构建。
 * Wrapper 为空时不生成 WHERE，即查全表（含已删），用于回收站列表与已删数据审计
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see CustomSqlInjector
 * @see BaseMapper#selectListWithDeleted
 */
public class SelectListWithDeleted extends AbstractMethod {

    /**
     * WHERE 条件脚本：只取 Wrapper 自身条件（不带逻辑删除过滤），条件为空时不生成 WHERE
     */
    private static final String SQL_WHERE_WRAPPER =
            "<if test=\"ew != null and ew.sqlSegment != null and ew.sqlSegment != ''\"> WHERE ${ew.sqlSegment}</if>";

    /**
     * 查询模板
     * <p> 占位符依次为 sqlFirst()、查询列、表名、WHERE 条件、sqlComment()
     */
    private static final String SQL_SELECT_LIST_WITH_DELETED = "<script>%s SELECT %s FROM %s %s %s</script>";

    /**
     * 声明注入的方法名
     *
     * <p> 字符串必须与 Mapper 上声明的方法名逐字一致，否则调用方抛 {@code BindingException}
     *
     * <pre>{@code List<T> selectListWithDeleted(@Param(Constants.WRAPPER) Wrapper<T> wrapper);}</pre>
     */
    public SelectListWithDeleted() {
        super("selectListWithDeleted");
    }

    /**
     * {@inheritDoc}
     * <p> 全表可用，无注入前置条件
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_SELECT_LIST_WITH_DELETED,
                sqlFirst(), sqlSelectColumns(tableInfo, false), tableInfo.getTableName(), SQL_WHERE_WRAPPER, sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo);
    }

}
