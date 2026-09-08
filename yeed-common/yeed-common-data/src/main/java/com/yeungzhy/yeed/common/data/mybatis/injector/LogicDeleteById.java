package com.yeungzhy.yeed.common.data.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;
import com.yeungzhy.yeed.common.data.mybatis.CustomSqlInjector;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 注入 {@code logicDeleteById}：根据主键逻辑删除，把逻辑删除列置为删除时间戳并记录删除人
 *
 * <p> WHERE 携带“未删除”条件，重复删除返回 0（天然幂等）；
 * 未删除值取自 {@code @TableLogic} 元数据而非硬编码，改用全局配置无需改本类
 *
 * <p> 不做自动填充：删除时间戳与删除人由 {@link BaseMapper} 的 default 方法计算后传入，
 * 业务侧统一走 {@code deleteByIdAutoFill}
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see CustomSqlInjector
 * @see BaseMapper#logicDeleteById
 */
public class LogicDeleteById extends AbstractMethod {

    /**
     * 逻辑删除 UPDATE 模板
     * <p> 占位符依次为表名、逻辑删除列、删除人列、主键列、逻辑删除列、未删除值；
     * 刻意不留 sqlComment() 位置：其脚本引用 {@code ew.sqlComment}，而本方法参数没有 Wrapper，
     * 拼进去会在 OGNL 解析阶段失败
     */
    private static final String SQL_LOGIC_DELETE_BY_ID =
            "<script>UPDATE %s SET %s=#{deleteTime}, %s=#{deleteBy} WHERE %s=#{id} AND %s=%s</script>";

    /**
     * 声明注入的方法名
     *
     * <p> 字符串必须与 Mapper 上声明的方法名逐字一致，否则调用方抛 {@code BindingException}
     *
     * <pre>{@code int logicDeleteById(@Param("id") Long id, @Param("deleteTime") long deleteTime, @Param("deleteBy") Long deleteBy);}</pre>
     */
    public LogicDeleteById() {
        super("logicDeleteById");
    }

    /**
     * {@inheritDoc}
     * <p> 注入前置条件不满足时 fail-fast：非 {@code @TableLogic} 表或缺 {@code deleteBy} 字段，
     * 启动期即断言失败，不留到运行期
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        Assert.isTrue(tableInfo.isWithLogicDelete(), "logicDeleteById 仅支持配置了逻辑删除(@TableLogic)的表: %s", tableInfo.getTableName());
        TableFieldInfo logicDeleteField = tableInfo.getLogicDeleteFieldInfo();
        // 按属性名匹配删除人列：缺该字段直接注入期失败，记录不了删除人本方法就失去意义
        TableFieldInfo deleteByField = tableInfo.getFieldList().stream()
                .filter(field -> "deleteBy".equals(field.getProperty()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "logicDeleteById 要求实体存在 deleteBy（删除人）字段: " + tableInfo.getEntityType().getName()));
        String sql = String.format(SQL_LOGIC_DELETE_BY_ID,
                tableInfo.getTableName(),
                logicDeleteField.getColumn(), deleteByField.getColumn(),
                tableInfo.getKeyColumn(),
                // 未删除值取自配置 mybatis-plus.global-config.db-config.logic-not-delete-value，不硬编码
                logicDeleteField.getColumn(), logicDeleteField.getLogicNotDeleteValue());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
        return addUpdateMappedStatement(mapperClass, modelClass, methodName, sqlSource);
    }

}
