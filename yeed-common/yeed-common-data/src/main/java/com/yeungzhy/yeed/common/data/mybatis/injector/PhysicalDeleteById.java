package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 注入 {@code physicalDeleteById}：根据主键物理删除，绕过 {@code @TableLogic} 过滤
 *
 * <p>逻辑删除表上 MyBatis-Plus 内置的 {@code deleteById} 会被框架改写成 UPDATE，
 * 本方法注入真正的 DELETE，用于回收站彻底删除，不可恢复
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see CustomSqlInjector
 * @see BaseMapper#physicalDeleteById
 */
public class PhysicalDeleteById extends AbstractMethod {

    /**
     * 物理删除模板
     * <p>占位符依次为表名、主键列、主键属性；
     * 刻意不留 sqlComment() 位置——其脚本引用 {@code ew.sqlComment}，而本方法参数没有 Wrapper，
     * 拼进去会在 OGNL 解析阶段失败
     */
    private static final String SQL_PHYSICAL_DELETE_BY_ID = "<script>DELETE FROM %s WHERE %s=#{%s}</script>";

    /**
     * 声明注入的方法名
     * <p>字符串必须与 Mapper 上声明的方法名逐字一致，否则调用方抛 {@code BindingException}：
     * <pre>{@code
     * int physicalDeleteById(Serializable id);
     * }</pre>
     */
    public PhysicalDeleteById() {
        super("physicalDeleteById");
    }

    /**
     * {@inheritDoc}
     * <p>全表可用，无注入前置条件
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_PHYSICAL_DELETE_BY_ID,
                tableInfo.getTableName(), tableInfo.getKeyColumn(), tableInfo.getKeyProperty());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
        return addDeleteMappedStatement(mapperClass, methodName, sqlSource);
    }

}
