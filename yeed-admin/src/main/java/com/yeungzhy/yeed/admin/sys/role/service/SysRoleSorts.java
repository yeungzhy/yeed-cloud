package com.yeungzhy.yeed.admin.sys.role.service;

import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 系统角色 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
@Component
public class SysRoleSorts extends BaseSorts<SysRole> {

    protected SysRoleSorts() {
        super(SysRole.class, new BaseSorts.Builder<SysRole>()
                .add(BaseEntity.Fields.createTime, SysRole::getCreateTime)
                // 多字段排序时必须带主键做决胜字段，否则排序键相同的行在翻页时可能错位或漏行
                .add(BaseEntity.Fields.id, SysRole::getId)
        );
    }

}
