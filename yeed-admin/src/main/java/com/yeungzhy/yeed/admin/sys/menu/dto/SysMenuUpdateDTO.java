package com.yeungzhy.yeed.admin.sys.menu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 系统菜单权限表 更新入参
 * <p>在新增入参 {@link SysMenuSaveDTO} 之上增加主键 id 与乐观锁 version——
 * id 定位待更新记录，version 由编辑表单回显，配合 updateById 做并发控制（版本号不匹配即更新失败）。
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
