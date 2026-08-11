package com.yeungzhy.yeed.admin.sys.menu.service;

import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 系统菜单权限表 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
 */
@Component
public class SysMenuSorts extends BaseSorts<SysMenu> {

    protected SysMenuSorts() {
        super(SysMenu.class, new BaseSorts.Builder<SysMenu>()
                .add(BaseEntity.Fields.createTime, SysMenu::getCreateTime)
        );
    }

}
