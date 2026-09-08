package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 注入 {@code selectPageWithDeleted}：根据 Wrapper 条件分页查询，包含已逻辑删除的数据
 *
 * <p> WHERE 规则与 {@link SelectListWithDeleted} 完全一致（只取 {@code ew.sqlSegment}）；
 * 分页由 {@link PaginationInnerInterceptor} 依据 {@link IPage} 参数完成，本方法只负责产出不带过滤的查询 SQL
 *
 * @author yeungzhy
 * @since 2026-08-13
 * @see SelectListWithDeleted
 * @see CustomSqlInjector
 * @see BaseMapper#selectPageWithDeleted
 */
public class SelectPageWithDeleted extends AbstractMethod {

    /**
     * WHERE 条件脚本：只取 Wrapper 自身条件（不带逻辑删除过滤），条件为空时不生成 WHERE
     */
    private static final String SQL_WHERE_WRAPPER =
            "<if test=\"ew != null and ew.sqlSegment != null and ew.sqlSegment != ''\"> WHERE ${ew.sqlSegment}</if>";

    /**
     * 查询模板
     * <p> 占位符依次为 sqlFirst()、查询列、表名、WHERE 条件、sqlComment()
     */
    private static final String SQL_SELECT_PAGE_WITH_DELETED = "<script>%s SELECT %s FROM %s %s %s</script>";

    /**
     * 声明注入的方法名
     *
     * <p> 字符串必须与 Mapper 上声明的方法名逐字一致，否则调用方抛 {@code BindingException}
     *
     * <pre>{@code IPage<T> selectPageWithDeleted(IPage<T> page, @Param(Constants.WRAPPER) Wrapper<T> wrapper);}</pre>
     */
    public SelectPageWithDeleted() {
        super("selectPageWithDeleted");
    }

    /**
     * {@inheritDoc}
     * <p> 全表可用，无注入前置条件
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_SELECT_PAGE_WITH_DELETED,
                sqlFirst(), sqlSelectColumns(tableInfo, false), tableInfo.getTableName(), SQL_WHERE_WRAPPER, sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo);
    }

}
