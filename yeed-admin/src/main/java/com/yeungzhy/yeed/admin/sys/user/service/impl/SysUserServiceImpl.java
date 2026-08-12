package com.yeungzhy.yeed.admin.sys.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.Collection;

import com.yeungzhy.yeed.admin.sys.user.dto.SysUserDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserMapper;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserSorts;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserConvert;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统用户 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private SysUserSorts sysUserSorts;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysUserConvert sysUserConvert;


    @Override
    public Long save(SysUserDTO dto) {
        // DTO -> Entity：同名字段由 MapStruct 自动映射
        SysUser entity = sysUserConvert.toEntity(dto);
        sysUserMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public void update(SysUserDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        // DTO -> Entity：id 与业务字段均自动映射
        SysUser entity = sysUserConvert.toEntity(dto);
        sysUserMapper.updateById(entity);
    }


    @Override
    public SysUserVO detail(Long id) {
        SysUser entity = sysUserMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return sysUserConvert.toVO(entity);
    }


    @Override
    public PageResult<SysUserVO> page(SysUserPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysUser> lambdaQuery = Wrappers.<SysUser>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysUserSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return sysUserMapper.selectPageVO(dto, lambdaQuery, sysUserConvert::toVO);
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysUserMapper.deleteByIdAutoFill(id);
    }


    @Override
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        // 批量逻辑删除：空集合不触库，超量自动分片，删除人自动填充
        sysUserMapper.deleteByIdsAutoFill(ids);
    }


    @Override
    public PageResult<SysUserVO> pageWithDeleted(SysUserPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysUser> lambdaQuery = Wrappers.<SysUser>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysUserSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        // 回收站分页：查询包含已逻辑删除的数据（与 page 的区别是不携带条件 delete_time = 0）
        return sysUserMapper.selectPageVOWithDeleted(dto, lambdaQuery, sysUserConvert::toVO);
    }


    @Override
    public void restoreById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 恢复已逻辑删除的数据；对未删除数据执行返回 0（天然幂等）
        BizAssert.isTrue(sysUserMapper.restoreById(id) > 0, "记录不存在或未删除");
    }


    @Override
    public void physicalDeleteById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 物理删除（真 DELETE）：不可恢复，仅用于"回收站彻底删除"等场景
        sysUserMapper.physicalDeleteById(id);
    }


}
