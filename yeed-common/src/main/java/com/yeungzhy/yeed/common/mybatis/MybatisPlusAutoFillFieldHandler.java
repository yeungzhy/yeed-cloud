package com.yeungzhy.yeed.common.mybatis;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yeungzhy.yeed.common.model.BaseEntity;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * MybatisPlus 自动填充字段处理器
 *
 * @author yeungzhy at 2026-08-01 22:09
 */
public class MybatisPlusAutoFillFieldHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {

        this.strictInsertFill(metaObject, BaseEntity.Fields.createTime, LocalDateTime.class, LocalDateTime.now());

        /*
         * 1、用户角色表、角色菜单表等关联表，没有这些通用字段
         * 2、非用户操作场景下，没有登录信息
         * 于是把框架底层的判断拿来手动判断：「有字段 && 值为空」才设置值。
         * 此时获取登录信息再失败，那就该排查问题了
         */
        boolean hasCreateByField = metaObject.hasGetter(BaseEntity.Fields.createBy);
        if (hasCreateByField && Objects.isNull(metaObject.getValue(BaseEntity.Fields.createBy))) {
            this.strictInsertFill(metaObject, BaseEntity.Fields.createBy, Long.class, StpUtil.getLoginId(RandomUtil.randomLong()));
        }

    }

    @Override
    public void updateFill(MetaObject metaObject) {

        this.strictUpdateFill(metaObject, BaseEntity.Fields.updateTime, LocalDateTime.class, LocalDateTime.now());

        /*
         * 1、用户角色表、角色菜单表等关联表，没有这些通用字段
         * 2、非用户操作场景下，没有登录信息
         * 于是把框架底层的判断拿来手动判断：「有字段 && 值为空」才设置值。
         * 此时获取登录信息再失败，那就该排查问题了
         */
        boolean hasCreateByField = metaObject.hasGetter(BaseEntity.Fields.updateBy);
        if (hasCreateByField && Objects.isNull(metaObject.getValue(BaseEntity.Fields.updateBy))) {
            this.strictUpdateFill(metaObject, BaseEntity.Fields.updateBy, Long.class, StpUtil.getLoginId(RandomUtil.randomLong()));
        }

    }
}
