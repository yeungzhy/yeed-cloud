package com.yeungzhy.yeed.admin.sys.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserMapper;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserSorts;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.data.support.MybatisPageConverters;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统用户 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:22:12
 */
@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private SysUserSorts sysUserSorts;
    @Resource
    private SysUserMapper sysUserMapper;


    @Override
    public Long save(SysUserDTO dto) {
        SysUser entity = new SysUser();
        // TODO 字段赋值：dto -> entity
        sysUserMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public SysUserVO detail(Long id) {
        SysUser entity = sysUserMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // TODO 字段转换：entity -> vo
        return new SysUserVO();
    }


    @Override
    public void update(SysUserDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        SysUser entity = new SysUser();
        // TODO 字段赋值：dto -> entity（id 必须赋值）
        sysUserMapper.updateById(entity);
    }


    @Override
    public PageResult<SysUserVO> page(SysUserPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysUser> lambdaQuery = Wrappers.<SysUser>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysUserSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        Page<SysUser> page = MybatisPageConverters.toMybatisPlusPage(dto);
        sysUserMapper.selectPage(page, lambdaQuery);

        return MybatisPageConverters.toPageResult(page, entity -> {
            // TODO 字段转换：entity -> vo
            return new SysUserVO();
        });
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysUserMapper.deleteByIdAutoFill(id);
    }


}
