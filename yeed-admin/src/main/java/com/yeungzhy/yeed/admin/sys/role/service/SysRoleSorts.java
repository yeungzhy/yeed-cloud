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
                // 多个字段排序时,要加上主键作为决胜字段,防止在其他字段相同的发生数据错乱或漏页
                .add(BaseEntity.Fields.id, SysRole::getId)
        );
    }

}
