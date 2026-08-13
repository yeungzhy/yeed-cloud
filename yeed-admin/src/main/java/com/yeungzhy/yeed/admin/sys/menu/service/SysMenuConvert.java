package com.yeungzhy.yeed.admin.sys.menu.service;

import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuDTO;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuPageVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuTreeVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import org.mapstruct.Mapper;

/**
 * 系统菜单权限表 对象字段映射器（编译期生成实现类）
 *
 * <p>Entity / DTO / VO 三模型字段同名，由 MapStruct 自动映射，无需手写 setter；
 * 模型字段变更后重新编译即可同步实现类，杜绝手写转换代码与模型字段脱节。
 * <p>声明为 Spring Bean（componentModel = spring），在 Service 实现中直接注入使用。
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Mapper(componentModel = "spring")
public interface SysMenuConvert {

    /** Entity → VO（详情出参，含审计字段与 version，供编辑回显） */
    SysMenuVO toVO(SysMenu entity);

    /** Entity → PageVO（分页列表出参，仅业务字段） */
    SysMenuPageVO toPageVO(SysMenu entity);

    /** Entity → TreeVO（树节点出参，仅业务字段；children 由 Service 组装） */
    SysMenuTreeVO toTreeVO(SysMenu entity);

    /** DTO → Entity（save / update 入参） */
    SysMenu toEntity(SysMenuDTO dto);

}
