package com.yeungzhy.yeed.common.data.mybatis;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.injector.methods.AlwaysUpdateSomeColumnById;
import com.yeungzhy.yeed.common.data.mybatis.injector.*;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * 自定义 SQL 注入器：在 MyBatis-Plus 内置方法基础上追加项目自定义方法
 * <p>详述: 保留 {@link DefaultSqlInjector} 注入的全部内置方法（insert/update/select/delete 等）, 额外追加:
 * <ul>
 *     <li>{@link ExistsByWrapper}: EXISTS 高性能存在性判断, 所有表可用</li>
 *     <li>{@link AlwaysUpdateSomeColumnById}: MyBatis-Plus 官方的"全字段更新（含 null）", 所有表可用</li>
 *     <li>{@link PhysicalDeleteById}/{@link PhysicalDelete}/{@link SelectListWithDeleted}/{@link RestoreById}:
 *         逻辑删除逃逸四件套（物理删除、带删查询、恢复）, 仅注入配置了 @TableLogic 的逻辑删除表</li>
 *     <li>{@link LogicDeleteById}: 根据主键逻辑删除（固定 SQL, 删除时间戳/删除人由调用方传参）,
 *         仅注入配置了 @TableLogic 且实体含 deleteBy 字段的表</li>
 *     <li>{@link LogicDeleteByIds}: 根据主键集合批量逻辑删除（IN 固定 SQL, 删除时间戳/删除人由调用方传参）,
 *         注入条件同 {@link LogicDeleteById}</li>
 * </ul>
 * <p>注意: 需在配置类中注册为 Spring Bean, MyBatis-Plus 检测到容器中存在 SqlInjector 时会替代默认注入器
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
public class CustomSqlInjector extends DefaultSqlInjector {

    @Override
    public List<AbstractMethod> getMethodList(Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methodList = super.getMethodList(configuration, mapperClass, tableInfo);
        methodList.add(new ExistsByWrapper());
        methodList.add(new AlwaysUpdateSomeColumnById());
        if (tableInfo.isWithLogicDelete()) {
            methodList.add(new LogicDeleteById());
            methodList.add(new LogicDeleteByIds());
            methodList.add(new PhysicalDeleteById());
            methodList.add(new PhysicalDelete());
            methodList.add(new SelectListWithDeleted());
            methodList.add(new RestoreById());
        }
        return methodList;
    }

}
