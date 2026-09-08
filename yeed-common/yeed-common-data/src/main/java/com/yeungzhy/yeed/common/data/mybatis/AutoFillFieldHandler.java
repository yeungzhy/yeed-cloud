package com.yeungzhy.yeed.common.data.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * MyBatis-Plus 审计字段自动填充处理器
 *
 * <p> 实现 {@link MetaObjectHandler}，insert 时填充 createTime/createBy，
 * update 时填充 updateTime/updateBy，由 {@link com.yeungzhy.yeed.common.data.config.MybatisPlusAutoConfiguration} 注册
 *
 * <p> createBy/updateBy 要先判「实体有该字段 && 值为空」才填：
 * 用户角色表、角色菜单表这类关联表压根没有这两个列，定时任务、启动初始化、异步线程等非用户操作场景
 * 没有登录态（由调用方显式传值）。两者都取不到登录用户时由 {@code LoginUserHelper.getUserId(null)}
 * 兜底为 null，再失败就属配置问题，需排查
 *
 * @author yeungzhy
 * @since 2026-08-01
 */
public class AutoFillFieldHandler implements MetaObjectHandler {

    /**
     * {@inheritDoc}
     *
     * <p> createTime 无条件覆盖；createBy 仅在实体有该字段且值为空时填，已在类注释说明
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, BaseEntity.Fields.createTime, LocalDateTime.class, LocalDateTime.now());

        boolean hasCreateByField = metaObject.hasGetter(BaseEntity.Fields.createBy);
        if (hasCreateByField && Objects.isNull(metaObject.getValue(BaseEntity.Fields.createBy))) {
            this.strictInsertFill(metaObject, BaseEntity.Fields.createBy, Long.class, LoginUserHelper.getUserId(null));
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p> updateTime 无条件覆盖；updateBy 沿用 createBy 的填充策略
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, BaseEntity.Fields.updateTime, LocalDateTime.class, LocalDateTime.now());

        boolean hasUpdateByField = metaObject.hasGetter(BaseEntity.Fields.updateBy);
        if (hasUpdateByField && Objects.isNull(metaObject.getValue(BaseEntity.Fields.updateBy))) {
            this.strictUpdateFill(metaObject, BaseEntity.Fields.updateBy, Long.class, LoginUserHelper.getUserId(null));
        }
    }
}
