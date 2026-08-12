package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 自定义 SQL 方法：根据主键恢复已逻辑删除的数据（restoreById）
 *
 * <p>详述: 继承 {@link AbstractMethod} 实现 SQL 注入, 将逻辑删除字段置回"未删除"值,
 * 用于"回收站恢复"场景; 配合 {@link CustomSqlInjector} 注册后, 所有 Mapper 均可声明方法调用（方法签名示例见类注释末尾）
 *
 * <p>生成的 SQL 形如: {@code UPDATE yeed_sys_user SET delete_time = 0, delete_by = NULL WHERE id = ? AND delete_time != 0}
 *
 * <p>注意: WHERE 携带"已删除"条件, 对未删除数据执行返回 0（天然幂等）;
 * 项目存在 deleteBy（删除人）字段时一并置 NULL, 避免恢复后残留删除痕迹;
 * 本方法不做自动填充, 如需记录恢复人/恢复时间, 由业务层在恢复后自行更新;
 * 本方法参数不含 Wrapper, 不能携带 sqlComment()（其脚本引用 ew.sqlComment 会导致 OGNL 解析失败）;
 *
 * <p>调用方声明:
 * <pre>
 *     {@code int restoreById(java.io.Serializable id);}
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public class RestoreById extends AbstractMethod {

    /**
     * 恢复 SQL 模板
     * <p>占位符依次为: 表名、SET 子句、主键列、主键属性、逻辑删除列、未删除值
     */
    private static final String SQL_RESTORE_BY_ID = "<script>UPDATE %s SET %s WHERE %s=#{%s} AND %s &lt;&gt; %s</script>";

    public RestoreById() {
        super("restoreById");
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        Assert.isTrue(tableInfo.isWithLogicDelete(), "restoreById 仅支持配置了逻辑删除(@TableLogic)的表: %s", tableInfo.getTableName());
        TableFieldInfo logicDeleteField = tableInfo.getLogicDeleteFieldInfo();
        String notDeleteValue = logicDeleteField.getLogicNotDeleteValue();
        // 逻辑删除列置回未删除值; 表上若存在删除人(deleteBy)字段则一并置 NULL, 避免恢复后残留删除痕迹
        StringBuilder setSql = new StringBuilder(logicDeleteField.getColumn()).append(" = ").append(notDeleteValue);
        tableInfo.getFieldList().stream()
                .filter(field -> "deleteBy".equals(field.getProperty()))
                .findFirst()
                .ifPresent(field -> setSql.append(", ").append(field.getColumn()).append(" = NULL"));
        String sql = String.format(SQL_RESTORE_BY_ID,
                tableInfo.getTableName(), setSql, tableInfo.getKeyColumn(), tableInfo.getKeyProperty(),
                logicDeleteField.getColumn(), notDeleteValue);
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
        return addUpdateMappedStatement(mapperClass, modelClass, methodName, sqlSource);
    }

}
