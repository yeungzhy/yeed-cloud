package com.yeungzhy.yeed.common.mybatis;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yeungzhy.yeed.common.Constant;
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

        this.strictInsertFill(metaObject, Constant.MYBATIS_PLUS_AUTO_FILL_CREATE_TIME, LocalDateTime.class, LocalDateTime.now());

        /*
         * 1、用户角色表、角色菜单表等关联表，没有这些通用字段
         * 2、非用户操作场景下，没有登录信息
         * 于是把框架底层的判断拿来手动判断：「有字段 && 值为空」才设置值。
         * 此时获取登录信息再失败，那就该排查问题了
         */
        boolean hasCreateByField = metaObject.hasGetter(Constant.MYBATIS_PLUS_AUTO_FILL_CREATE_BY);
        if (hasCreateByField && Objects.isNull(metaObject.getValue(Constant.MYBATIS_PLUS_AUTO_FILL_CREATE_BY))) {
            this.strictInsertFill(metaObject, Constant.MYBATIS_PLUS_AUTO_FILL_CREATE_BY, Long.class, StpUtil.getLoginId(RandomUtil.randomLong()));
        }

    }

    @Override
    public void updateFill(MetaObject metaObject) {

        this.strictInsertFill(metaObject, Constant.MYBATIS_PLUS_AUTO_FILL_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
        boolean isHasVal = metaObject.hasGetter(Constant.MYBATIS_PLUS_AUTO_FILL_DELETE_TIME);
        Object value = metaObject.getValue(Constant.MYBATIS_PLUS_AUTO_FILL_DELETE_TIME);

        /*
         * 1、用户角色表、角色菜单表等关联表，没有这些通用字段
         * 2、非用户操作场景下，没有登录信息
         * 于是把框架底层的判断拿来手动判断：「有字段 && 值为空」才设置值。
         * 此时获取登录信息再失败，那就该排查问题了
         */
        boolean hasCreateByField = metaObject.hasGetter(Constant.MYBATIS_PLUS_AUTO_FILL_UPDATE_BY);
        if (hasCreateByField && Objects.isNull(metaObject.getValue(Constant.MYBATIS_PLUS_AUTO_FILL_UPDATE_BY))) {
            this.strictInsertFill(metaObject, Constant.MYBATIS_PLUS_AUTO_FILL_UPDATE_BY, Long.class, StpUtil.getLoginId(RandomUtil.randomLong()));
        }

    }
}
