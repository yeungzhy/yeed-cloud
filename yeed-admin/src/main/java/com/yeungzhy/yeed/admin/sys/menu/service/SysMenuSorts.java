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
                // 多个字段排序时,要加上主键作为决胜字段,防止在其他字段相同的发生数据错乱或漏页
                .add(BaseEntity.Fields.id, SysMenu::getId)
        );
    }

}
