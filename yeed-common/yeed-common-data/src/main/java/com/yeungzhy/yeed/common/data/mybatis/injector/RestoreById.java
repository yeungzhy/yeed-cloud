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
 * 注入 {@code restoreById}：根据主键恢复已逻辑删除的数据
 *
 * <p>WHERE 携带“已删除”条件，对未删除的数据执行返回 0（天然幂等）；
 * 实体含 {@code deleteBy} 字段时一并置 NULL，避免恢复后残留上次删除痕迹
 *
 * <p>不做自动填充：需要记录恢复人 / 恢复时间，由业务层在恢复后自行更新
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see CustomSqlInjector
 * @see BaseMapper#restoreById
 */
public class RestoreById extends AbstractMethod {

    /**
     * 恢复 UPDATE 模板
     * <p>占位符依次为表名、SET 子句、主键列、主键属性、逻辑删除列、未删除值；
     * 模板里的不等号写作 {@code &lt;&gt;}：整段 SQL 走 XML 脚本解析，裸 {@code <>} 会被当成标签起始。
     * 刻意不留 sqlComment() 位置——其脚本引用 {@code ew.sqlComment}，而本方法参数没有 Wrapper，
     * 拼进去会在 OGNL 解析阶段失败
     */
    private static final String SQL_RESTORE_BY_ID = "<script>UPDATE %s SET %s WHERE %s=#{%s} AND %s &lt;&gt; %s</script>";

    /**
     * 声明注入的方法名
     * <p>字符串必须与 Mapper 上声明的方法名逐字一致，否则调用方抛 {@code BindingException}：
     * <pre>{@code
     * int restoreById(Serializable id);
     * }</pre>
     */
    public RestoreById() {
        super("restoreById");
    }

    /**
     * {@inheritDoc}
     * <p>注入前置条件不满足时 fail-fast：非 {@code @TableLogic} 表，启动期即断言失败，不留到运行期
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        Assert.isTrue(tableInfo.isWithLogicDelete(), "restoreById 仅支持配置了逻辑删除(@TableLogic)的表: %s", tableInfo.getTableName());
        TableFieldInfo logicDeleteField = tableInfo.getLogicDeleteFieldInfo();
        String notDeleteValue = logicDeleteField.getLogicNotDeleteValue();
        // 置回未删除值；存在 deleteBy 字段时一并置 NULL，否则恢复后仍残留上次删除人
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
