package com.yeungzhy.yeed.admin.sys.menu.service;

import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * 系统菜单权限表 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Component
public class SysMenuSorts extends BaseSorts<SysMenu> {

    protected SysMenuSorts() {
        super(SysMenu.class, new BaseSorts.Builder<SysMenu>()
                .add(BaseEntity.Fields.createTime, SysMenu::getCreateTime)
                // 多字段排序时必须带主键做决胜字段，否则排序键相同的行在翻页时可能错位或漏行
                .add(BaseEntity.Fields.id, SysMenu::getId)
        );
    }

}
