package com.yeungzhy.yeed.admin.sys.menu.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 菜单拖拽调整层级入参（仅更新 parentId）
 *
 * <p>与整表单 {@link SysMenuDTO} 语义不同：move 只做层级移动、不触碰其它字段，
 * 按"接口参数隔离"约定独立成类，避免把无关字段暴露给前端。
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Data
@Accessors(chain = true)
public class SysMenuMoveDTO {

    /** 被移动的菜单 ID */
    private Long id;

    /** 目标父级 ID；null 或 0 表示移动到顶级 */
    private Long parentId;

}
