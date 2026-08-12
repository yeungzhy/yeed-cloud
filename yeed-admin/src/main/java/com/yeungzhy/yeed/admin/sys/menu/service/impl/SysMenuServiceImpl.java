package com.yeungzhy.yeed.admin.sys.menu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.Collection;

import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuPageDTO;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuSorts;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuConvert;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统菜单权限表 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Slf4j
@Service
public class SysMenuServiceImpl implements SysMenuService {

    @Resource
    private SysMenuSorts sysMenuSorts;
    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private SysMenuConvert sysMenuConvert;


    @Override
    public Long save(SysMenuDTO dto) {
        // DTO -> Entity：同名字段由 MapStruct 自动映射
        SysMenu entity = sysMenuConvert.toEntity(dto);
        sysMenuMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public void update(SysMenuDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        // DTO -> Entity：id 与业务字段均自动映射
        SysMenu entity = sysMenuConvert.toEntity(dto);
        sysMenuMapper.updateById(entity);
    }


    @Override
    public SysMenuVO detail(Long id) {
        SysMenu entity = sysMenuMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return sysMenuConvert.toVO(entity);
    }


    @Override
    public PageResult<SysMenuVO> page(SysMenuPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysMenu> lambdaQuery = Wrappers.<SysMenu>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysMenuSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return sysMenuMapper.selectPageVO(dto, lambdaQuery, sysMenuConvert::toVO);
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysMenuMapper.deleteByIdAutoFill(id);
    }


    @Override
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        // 批量逻辑删除：空集合不触库，超量自动分片，删除人自动填充
        sysMenuMapper.deleteByIdsAutoFill(ids);
    }


    @Override
    public PageResult<SysMenuVO> pageWithDeleted(SysMenuPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysMenu> lambdaQuery = Wrappers.<SysMenu>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysMenuSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        // 回收站分页：查询包含已逻辑删除的数据（与 page 的区别是不携带条件 delete_time = 0）
        return sysMenuMapper.selectPageVOWithDeleted(dto, lambdaQuery, sysMenuConvert::toVO);
    }


    @Override
    public void restoreById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 恢复已逻辑删除的数据；对未删除数据执行返回 0（天然幂等）
        BizAssert.isTrue(sysMenuMapper.restoreById(id) > 0, "记录不存在或未删除");
    }


    @Override
    public void physicalDeleteById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 物理删除（真 DELETE）：不可恢复，仅用于"回收站彻底删除"等场景
        sysMenuMapper.physicalDeleteById(id);
    }


}
