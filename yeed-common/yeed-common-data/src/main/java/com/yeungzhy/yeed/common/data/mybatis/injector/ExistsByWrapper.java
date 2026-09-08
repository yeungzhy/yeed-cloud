package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 注入 {@code existsByWrapper}：根据 Wrapper 条件判断记录是否存在
 *
 * <p> 生成 EXISTS 子查询，命中首条记录即短路返回，相比 {@code COUNT(*)} 不必扫描全部匹配行；
 * WHERE 交由 {@link #sqlWhereEntityWrapper} 生成，自动带上 {@code @TableLogic} 未删除条件
 *
 * <pre>{@code SELECT EXISTS (SELECT 1 FROM yeed_sys_user WHERE (username = ?))}</pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see CustomSqlInjector
 * @see BaseMapper#existsByWrapper
 */
public class ExistsByWrapper extends AbstractMethod {

    /**
     * EXISTS 子查询模板
     * <p> 占位符依次为 sqlFirst()、表名、WHERE 条件、sqlComment()
     */
    private static final String SQL_EXISTS_BY_WRAPPER = "<script>SELECT EXISTS (SELECT 1 FROM %s %s %s %s)</script>";

    /**
     * 声明注入的方法名
     *
     * <p> 字符串必须与 Mapper 上声明的方法名逐字一致，否则调用方抛 {@code BindingException}
     *
     * <pre>{@code Boolean existsByWrapper(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper);}</pre>
     */
    public ExistsByWrapper() {
        super("existsByWrapper");
    }

    /**
     * {@inheritDoc}
     * <p> 全表可用，无注入前置条件
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_EXISTS_BY_WRAPPER,
                sqlFirst(), tableInfo.getTableName(), sqlWhereEntityWrapper(true, tableInfo), sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return addSelectMappedStatementForOther(mapperClass, methodName, sqlSource, Boolean.class);
    }

}
