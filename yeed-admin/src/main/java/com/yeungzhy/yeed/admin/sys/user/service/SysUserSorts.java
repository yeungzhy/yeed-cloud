package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.common.model.BaseEntity;
import com.yeungzhy.yeed.common.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * SysUser 排序字段白名单
 * <p> 仅暴露允许前端排序的字段；email/emailBidx/password 等敏感字段不在此列
 */
@Component
public class SysUserSorts extends BaseSorts<SysUser> {

    protected SysUserSorts() {
        super(SysUser.class, config -> config
                        .add(BaseEntity.Fields.createTime, SysUser::getCreateTime)
                        .add(SysUser.Fields.username, SysUser::getUsername)
        );
    }
}
