package com.yeungzhy.yeed.common.data.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * MyBatis-Plus 审计字段自动填充处理器。
 *
 * <p>实现 {@link MetaObjectHandler}，insert 时填充 createTime/createBy，
 * update 时填充 updateTime/updateBy，由 {@link com.yeungzhy.yeed.common.data.config.MybatisPlusAutoConfiguration} 注册。
 *
 * <p>createBy/updateBy 手动判断"有字段 && 值为空"才填充的原因：
 * <ul>
 *   <li>用户角色表、角色菜单表等关联表没有这两个字段</li>
 *   <li>定时任务、启动初始化等非用户操作场景没有登录信息（{@link LoginUserHelper} 取不到用户）</li>
 * </ul>
 * 此时获取登录信息仍失败，则属配置问题，应排查。
 *
 * @author yeungzhy
 * @since 2026-08-01
 */
public class AutoFillFieldHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, BaseEntity.Fields.createTime, LocalDateTime.class, LocalDateTime.now());

        // 仅当实体有 createBy 字段且值为空时才填充（兼容关联表无此字段、非用户操作无登录态）
        boolean hasCreateByField = metaObject.hasGetter(BaseEntity.Fields.createBy);
        if (hasCreateByField && Objects.isNull(metaObject.getValue(BaseEntity.Fields.createBy))) {
            this.strictInsertFill(metaObject, BaseEntity.Fields.createBy, Long.class, LoginUserHelper.requireUserId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, BaseEntity.Fields.updateTime, LocalDateTime.class, LocalDateTime.now());

        // 同上：updateBy 仅在实体有该字段且为空时填充
        boolean hasUpdateByField = metaObject.hasGetter(BaseEntity.Fields.updateBy);
        if (hasUpdateByField && Objects.isNull(metaObject.getValue(BaseEntity.Fields.updateBy))) {
            this.strictUpdateFill(metaObject, BaseEntity.Fields.updateBy, Long.class, LoginUserHelper.requireUserId());
        }
    }
}
