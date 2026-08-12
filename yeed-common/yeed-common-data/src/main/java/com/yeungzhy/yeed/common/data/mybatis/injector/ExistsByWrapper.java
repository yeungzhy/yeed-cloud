package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 自定义 SQL 方法：根据 Wrapper 条件判断数据是否存在（existsByWrapper）
 *
 * <p>详述: 继承 {@link AbstractMethod} 实现 SQL 注入, 生成 EXISTS 子查询 SQL, 数据库命中首条记录即短路返回,
 * 相比 COUNT(*) 无需扫描全部匹配行; 配合 {@link CustomSqlInjector} 注册后,
 * 所有 Mapper 均可声明方法调用（方法签名示例见类注释末尾）
 *
 * <p>生成的 SQL 形如: {@code SELECT EXISTS (SELECT 1 FROM sys_user WHERE (username = ?))}
 *
 * <p>注意: WHERE 条件由 {@code sqlWhereEntityWrapper} 生成, 会自动携带逻辑删除(@TableLogic)过滤;
 *
 * <p>调用方声明:
 * <pre>
 *     {@code Boolean existsByWrapper(@Param(Constants.WRAPPER) Wrapper<SysUser> queryWrapper);}
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public class ExistsByWrapper extends AbstractMethod {

    /**
     * EXISTS 查询 SQL 模板
     * <p>占位符依次为: sqlFirst()、表名、WHERE 条件、sqlComment()
     */
    private static final String SQL_EXISTS_BY_WRAPPER = "<script>SELECT EXISTS (SELECT 1 FROM %s %s %s %s)</script>";

    public ExistsByWrapper() {
        super("existsByWrapper");
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_EXISTS_BY_WRAPPER,
                sqlFirst(), tableInfo.getTableName(), sqlWhereEntityWrapper(true, tableInfo), sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return addSelectMappedStatementForOther(mapperClass, methodName, sqlSource, Boolean.class);
    }

}
