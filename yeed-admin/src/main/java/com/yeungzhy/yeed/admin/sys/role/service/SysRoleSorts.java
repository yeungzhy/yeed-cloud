package com.yeungzhy.yeed.admin.sys.role.service;

import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.common.model.BaseEntity;
import com.yeungzhy.yeed.common.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 系统角色 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:37
 */
@Component
public class SysRoleSorts extends BaseSorts<SysRole> {

    protected SysRoleSorts() {
        super(SysRole.class, new BaseSorts.Builder<SysRole>()
                .add(BaseEntity.Fields.createTime, SysRole::getCreateTime)
        );
    }

}
