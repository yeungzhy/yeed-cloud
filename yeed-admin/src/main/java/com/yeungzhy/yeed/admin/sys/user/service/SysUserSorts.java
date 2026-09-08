package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 系统用户 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Component
public class SysUserSorts extends BaseSorts<SysUser> {

    protected SysUserSorts() {
        super(SysUser.class, new BaseSorts.Builder<SysUser>()
                .add(BaseEntity.Fields.createTime, SysUser::getCreateTime)
                // 多字段排序时必须带主键做决胜字段，否则排序键相同的行在翻页时可能错位或漏行
                .add(BaseEntity.Fields.id, SysUser::getId)
        );
    }

}
