package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 自定义 SQL 方法：根据主键物理删除, 忽略逻辑删除过滤（physicalDeleteById）
 *
 * <p> 详述: 继承 {@link AbstractMethod} 实现 SQL 注入, 逻辑删除表上 MyBatis-Plus 内置的 deleteById
 * 会被框架改写为 UPDATE, 本方法注入真正的 DELETE 语句, WHERE 不追加 delete_time 未删除条件,
 * 用于"回收站彻底删除"等场景; 配合 {@link CustomSqlInjector} 注册后,
 * 所有 Mapper 均可声明方法调用（方法签名示例见类注释末尾）
 *
 * <p> 生成的 SQL 形如: {@code DELETE FROM yeed_sys_user WHERE id = ?}
 *
 * <p> 注意: 物理删除不可恢复, 业务侧应确保只对已逻辑删除的数据执行;
 * 本方法参数不含 Wrapper, 不能携带 sqlComment()（其脚本引用 ew.sqlComment 会导致 OGNL 解析失败）;
 *
 * <p> 调用方声明:
 * <pre>
 *     {@code int physicalDeleteById(java.io.Serializable id);}
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public class PhysicalDeleteById extends AbstractMethod {

    /**
     * 物理删除 SQL 模板
     * <p> 占位符依次为: 表名、主键列、主键属性
     */
    private static final String SQL_PHYSICAL_DELETE_BY_ID = "<script>DELETE FROM %s WHERE %s=#{%s}</script>";

    public PhysicalDeleteById() {
        super("physicalDeleteById");
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = String.format(SQL_PHYSICAL_DELETE_BY_ID,
                tableInfo.getTableName(), tableInfo.getKeyColumn(), tableInfo.getKeyProperty());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
        return addDeleteMappedStatement(mapperClass, methodName, sqlSource);
    }

}
