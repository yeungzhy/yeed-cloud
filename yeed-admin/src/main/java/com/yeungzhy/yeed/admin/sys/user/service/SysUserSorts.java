package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.common.model.BaseEntity;
import com.yeungzhy.yeed.common.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 系统用户 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-08-09 11:55:36
 */
@Component
public class SysUserSorts extends BaseSorts<SysUser> {

    protected SysUserSorts() {
        super(SysUser.class, new BaseSorts.Builder<SysUser>()
                .add(BaseEntity.Fields.createTime, SysUser::getCreateTime)
        );
    }

}
