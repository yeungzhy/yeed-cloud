package com.yeungzhy.yeed.admin.sys.user.service;

import com.yeungzhy.yeed.admin.sys.user.dto.SysUserDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import org.mapstruct.Mapper;

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

}
