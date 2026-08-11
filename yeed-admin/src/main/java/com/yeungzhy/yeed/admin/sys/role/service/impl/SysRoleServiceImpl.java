package com.yeungzhy.yeed.admin.sys.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMapper;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleService;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleSorts;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.data.support.MybatisPageConverters;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统角色 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:37
 */
@Slf4j
@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Resource
    private SysRoleSorts sysRoleSorts;
    @Resource
    private SysRoleMapper sysRoleMapper;


    @Override
    public Long save(SysRoleDTO dto) {
        SysRole entity = new SysRole();
        // TODO 字段赋值：dto -> entity
        sysRoleMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public SysRoleVO detail(Long id) {
        SysRole entity = sysRoleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // TODO 字段转换：entity -> vo
        return new SysRoleVO();
    }


    @Override
    public void update(SysRoleDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        SysRole entity = new SysRole();
        // TODO 字段赋值：dto -> entity（id 必须赋值）
        sysRoleMapper.updateById(entity);
    }


    @Override
    public PageResult<SysRoleVO> page(SysRolePageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysRole> lambdaQuery = Wrappers.<SysRole>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysRoleSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        Page<SysRole> page = MybatisPageConverters.toMybatisPlusPage(dto);
        sysRoleMapper.selectPage(page, lambdaQuery);

        return MybatisPageConverters.toPageResult(page, entity -> {
            // TODO 字段转换：entity -> vo
            return new SysRoleVO();
        });
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysRoleMapper.deleteByIdAutoFill(id);
    }


}
