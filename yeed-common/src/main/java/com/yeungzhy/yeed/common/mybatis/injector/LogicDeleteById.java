package com.yeungzhy.yeed.common.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.yeungzhy.yeed.common.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 自定义 SQL 方法：根据主键逻辑删除（logicDeleteById）
 *
 * <p> 详述: 继承 {@link AbstractMethod} 实现 SQL 注入, 生成 UPDATE 语句将逻辑删除列置为"已删除"值、
 * 同时记录删除人; WHERE 携带"未删除"条件, 重复删除返回 0（天然幂等）;
 * 配合 {@link CustomSqlInjector} 注册后, 所有 Mapper 均可声明方法调用（方法签名示例见类注释末尾）
 *
 * <p> 生成的 SQL 形如: {@code UPDATE yeed_sys_user SET delete_time = ?, delete_by = ? WHERE id = ? AND delete_time = 0}
 *
 * <p> 注意: 本方法不做自动填充, 删除时间戳与删除人由 Mapper 接口 default 方法计算后传入,
 * 业务统一走自动填充入口 {@code deleteByIdAutoFill};
 * 未删除值动态取自逻辑删除元数据(@TableLogic 或全局配置), 不硬编码; 实体必须存在 deleteBy（删除人）字段,
 * 否则注入期直接断言失败; 本方法参数不含 Wrapper, 不能携带 sqlComment()（其脚本引用 ew.sqlComment 会导致 OGNL 解析失败）;
 *
 * <p> 调用方声明:
 * <pre>
 *     {@code int logicDeleteById(@Param("id") Long id, @Param("deleteTime") long deleteTime, @Param("deleteBy") Long deleteBy);}
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public class LogicDeleteById extends AbstractMethod {

    /**
     * 逻辑删除 SQL 模板
     * <p> 占位符依次为: 表名、逻辑删除列、删除人列、主键列、逻辑删除列、未删除值
     */
    private static final String SQL_LOGIC_DELETE_BY_ID =
            "<script>UPDATE %s SET %s=#{deleteTime}, %s=#{deleteBy} WHERE %s=#{id} AND %s=%s</script>";

    public LogicDeleteById() {
        super("logicDeleteById");
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        Assert.isTrue(tableInfo.isWithLogicDelete(), "logicDeleteById 仅支持配置了逻辑删除(@TableLogic)的表: %s", tableInfo.getTableName());
        TableFieldInfo logicDeleteField = tableInfo.getLogicDeleteFieldInfo();
        // 删除人列按属性名 deleteBy 匹配, 缺失则注入期断言失败（没有删除人记录, 本方法无意义）
        TableFieldInfo deleteByField = tableInfo.getFieldList().stream()
                .filter(field -> "deleteBy".equals(field.getProperty()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "logicDeleteById 要求实体存在 deleteBy（删除人）字段: " + tableInfo.getEntityType().getName()));
        String sql = String.format(SQL_LOGIC_DELETE_BY_ID,
                tableInfo.getTableName(),
                logicDeleteField.getColumn(), deleteByField.getColumn(),
                tableInfo.getKeyColumn(),
                // 从配置读取未删除值: mybatis-plus.global-config.db-config.logic-not-delete-value
                logicDeleteField.getColumn(), logicDeleteField.getLogicNotDeleteValue());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
        return addUpdateMappedStatement(mapperClass, modelClass, methodName, sqlSource);
    }

}
