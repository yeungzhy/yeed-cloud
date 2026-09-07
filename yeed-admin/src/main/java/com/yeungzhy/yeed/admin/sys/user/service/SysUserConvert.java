package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuVisibleEnum;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.api.export.user.dto.UserExportDTO;
import com.yeungzhy.yeed.api.export.user.dto.UserExportPageDTO;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 系统用户 对象字段映射器（编译期生成实现类）
 *
 * <p>Entity / DTO / VO 三模型字段同名，由 MapStruct 自动映射，无需手写 setter；
 * 模型字段变更后重新编译即可同步实现类，杜绝手写转换代码与模型字段脱节。
 * <p>声明为 Spring Bean（componentModel = spring），在 Service 实现中直接注入使用。
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Mapper(componentModel = "spring")
public interface SysUserConvert {

    /** Entity → VO（详情 / 分页列表出参） */
    SysUserVO toVO(SysUser entity);

    /** DTO → Entity（save / update 入参） */
    SysUser toEntity(SysUserDTO dto);

    /** DTO → Entity（update 入参） */
    SysUser toEntity(SysUserUpdateDTO dto);

    /** 导出Page -> 查询Page */
    SysUserPageDTO toPageDTO(UserExportPageDTO dto);

    /** Entity -> 导出 DTO */
    UserExportDTO toExportDTO(SysUserVO vo);

    /**
     * Entity → 用户菜单树节点（user-menus 出参）
     * <p>children 由 {@link com.yeungzhy.yeed.common.core.support.TreeUtil} 建树填充，此处显式忽略。
     */
    @Mapping(target = "children", ignore = true)
    MenuTreeInfo toMenuTreeInfo(SysMenu entity);

    /** 菜单类型枚举 → 整数 code（MapStruct 转换钩子，枚举不能自动映射到 Integer） */
    default Integer menuTypeToCode(MenuTypeEnum type) {
        return type == null ? null : type.getCode();
    }

    /** 可见性枚举 → 整数 code（MapStruct 转换钩子，枚举不能自动映射到 Integer） */
    default Integer menuVisibleToCode(MenuVisibleEnum visible) {
        return visible == null ? null : visible.getCode();
    }

}
