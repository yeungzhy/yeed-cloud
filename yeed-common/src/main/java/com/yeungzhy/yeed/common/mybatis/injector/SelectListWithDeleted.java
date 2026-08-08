package com.yeungzhy.yeed.common.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 自定义 SQL 方法：根据 Wrapper 条件查询列表, 包含已逻辑删除数据（selectListWithDeleted）
 *
 * <p> 详述: 继承 {@link AbstractMethod} 实现 SQL 注入, WHERE 只取 Wrapper 自身条件,
 * 不追加 delete_time 未删除条件, 用于"回收站列表、已删数据审计"等场景;
 * 配合 {@link CustomSqlInjector} 注册后, 所有 Mapper 均可声明方法调用（方法签名示例见类注释末尾）
 *
 * <p> 生成的 SQL 形如: {@code SELECT id, username, ... FROM yeed_sys_user WHERE (username = ?)}
 *
 * <p> 注意: WHERE 条件不复用 {@code sqlWhereEntityWrapper}（其会强制追加逻辑删除条件）, 只取 {@code ew.sqlSegment};
 * Wrapper 未携带条件时无 WHERE, 即查询全表（含已删）; Wrapper 上设置的 entity 条件不会生效,
 * 条件请全部通过 Wrapper 方法构建;
 *
 * <p> 调用方声明:
 * <pre>
 *     {@code java.util.List<SysUser> selectListWithDeleted(@Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);}
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public class SelectListWithDeleted extends AbstractMethod {

    /**
     * WHERE 条件脚本：取 Wrapper 条件（不带逻辑删除过滤）, 条件为空时不生成 WHERE
     */
    private static final String SQL_WHERE_WRAPPER =
            "<if test=\"ew != null and ew.sqlSegment != null and ew.sqlSegment != ''\"> WHERE ${ew.sqlSegment}</if>";

    /**
     * 查询 SQL 模板
     * <p> 占位符依次为: sqlFirst()、查询列、表名、WHERE 条件、sqlComment()
     */
    private static final String SQL_SELECT_LIST_WITH_DELETED = "<script>%s SELECT %s FROM %s %s %s</script>";

    public SelectListWithDeleted() {
        super("selectListWithDeleted");
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_SELECT_LIST_WITH_DELETED,
                sqlFirst(), sqlSelectColumns(tableInfo, false), tableInfo.getTableName(), SQL_WHERE_WRAPPER, sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo);
    }

}
