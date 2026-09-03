package com.yeungzhy.yeed.common.data.mybatis;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.injector.methods.AlwaysUpdateSomeColumnById;
import com.yeungzhy.yeed.common.data.mybatis.injector.ExistsByWrapper;
import com.yeungzhy.yeed.common.data.mybatis.injector.LogicDeleteById;
import com.yeungzhy.yeed.common.data.mybatis.injector.LogicDeleteByIds;
import com.yeungzhy.yeed.common.data.mybatis.injector.PhysicalDelete;
import com.yeungzhy.yeed.common.data.mybatis.injector.PhysicalDeleteById;
import com.yeungzhy.yeed.common.data.mybatis.injector.RestoreById;
import com.yeungzhy.yeed.common.data.mybatis.injector.SelectListWithDeleted;
import com.yeungzhy.yeed.common.data.mybatis.injector.SelectPageWithDeleted;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * 自定义 SQL 注入器：在 MyBatis-Plus 内置方法之上追加项目自定义方法
 *
 * <p>{@link DefaultSqlInjector} 注入的内置方法（insert / update / select / delete 等）原样保留，
 * 追加项分两类：
 * <ul>
 *     <li>全表注入：{@link ExistsByWrapper}、官方 {@link AlwaysUpdateSomeColumnById}；
 *     <li>仅逻辑删除表注入：{@link LogicDeleteById}、{@link LogicDeleteByIds}、{@link PhysicalDeleteById}、
 *         {@link PhysicalDelete}、{@link SelectListWithDeleted}、{@link SelectPageWithDeleted}、
 *         {@link RestoreById}——其中 logicDelete 两个还要求实体含 {@code deleteBy} 字段。
 * </ul>
 *
 * <p>逐表在启动期拼装并注册 SQL：表不满足注入条件时当场断言失败，不会拖到运行期才抛异常。
 *
 * <p>必须注册为 Spring Bean 才生效——MyBatis-Plus 发现容器里存在 {@code SqlInjector} 就用它替换默认注入器，
 * 注册入口见 {@link com.yeungzhy.yeed.common.data.config.MybatisPlusAutoConfiguration}。
 *
 * @author yeungzhy
 * @since 2026-08-08
 * @see BaseMapper
 */
public class CustomSqlInjector extends DefaultSqlInjector {

    /**
     * {@inheritDoc}
     * <p>追加项排在内置方法之后；逻辑删除相关方法仅在 {@link TableInfo#isWithLogicDelete()} 为真时追加，
     * 非逻辑删除表的 Mapper 上这些方法不存在，调用会抛 {@code BindingException}
     */
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
            methodList.add(new SelectPageWithDeleted());
            methodList.add(new RestoreById());
        }
        return methodList;
    }

}
