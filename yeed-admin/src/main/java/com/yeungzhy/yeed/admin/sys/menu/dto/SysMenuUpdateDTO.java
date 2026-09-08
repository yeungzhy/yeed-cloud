package com.yeungzhy.yeed.admin.sys.menu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统菜单 更新入参
 *
 * <p>在新增入参 {@link SysMenuSaveDTO} 之上加主键 id 定位待更新记录，
 * 乐观锁 version 由 {@code yeed_sys_menu} 行带出，本类不接收
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class SysMenuUpdateDTO extends SysMenuSaveDTO {

    /** 雪花ID主键（必填，定位待更新记录） */
    private Long id;

}
