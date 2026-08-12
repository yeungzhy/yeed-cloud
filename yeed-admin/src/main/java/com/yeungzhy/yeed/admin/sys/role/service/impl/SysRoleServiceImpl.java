package com.yeungzhy.yeed.admin.sys.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.Collection;

import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMapper;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleService;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleSorts;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleConvert;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统角色 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
@Slf4j
@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Resource
    private SysRoleSorts sysRoleSorts;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysRoleConvert sysRoleConvert;


    @Override
    public Long save(SysRoleDTO dto) {
        // DTO -> Entity：同名字段由 MapStruct 自动映射
        SysRole entity = sysRoleConvert.toEntity(dto);
        sysRoleMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public void update(SysRoleDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        // DTO -> Entity：id 与业务字段均自动映射
        SysRole entity = sysRoleConvert.toEntity(dto);
        sysRoleMapper.updateById(entity);
    }


    @Override
    public SysRoleVO detail(Long id) {
        SysRole entity = sysRoleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return sysRoleConvert.toVO(entity);
    }


    @Override
    public PageResult<SysRoleVO> page(SysRolePageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysRole> lambdaQuery = Wrappers.<SysRole>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysRoleSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return sysRoleMapper.selectPageVO(dto, lambdaQuery, sysRoleConvert::toVO);
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysRoleMapper.deleteByIdAutoFill(id);
    }


    @Override
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        // 批量逻辑删除：空集合不触库，超量自动分片，删除人自动填充
        sysRoleMapper.deleteByIdsAutoFill(ids);
    }


    @Override
    public PageResult<SysRoleVO> pageWithDeleted(SysRolePageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysRole> lambdaQuery = Wrappers.<SysRole>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysRoleSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        // 回收站分页：查询包含已逻辑删除的数据（与 page 的区别是不携带条件 delete_time = 0）
        return sysRoleMapper.selectPageVOWithDeleted(dto, lambdaQuery, sysRoleConvert::toVO);
    }


    @Override
    public void restoreById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 恢复已逻辑删除的数据；对未删除数据执行返回 0（天然幂等）
        BizAssert.isTrue(sysRoleMapper.restoreById(id) > 0, "记录不存在或未删除");
    }


    @Override
    public void physicalDeleteById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 物理删除（真 DELETE）：不可恢复，仅用于"回收站彻底删除"等场景
        sysRoleMapper.physicalDeleteById(id);
    }


}
