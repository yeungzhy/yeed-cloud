package com.yeungzhy.yeed.admin.sys.menu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuPageDTO;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuSorts;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import com.yeungzhy.yeed.common.exception.BizAssert;
import com.yeungzhy.yeed.common.result.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统菜单权限表 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
 */
@Slf4j
@Service
public class SysMenuServiceImpl implements SysMenuService {

    @Resource
    private SysMenuSorts sysMenuSorts;
    @Resource
    private SysMenuMapper sysMenuMapper;


    @Override
    public Long save(SysMenuDTO dto) {
        SysMenu entity = new SysMenu();
        // TODO 字段赋值：dto -> entity
        sysMenuMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public SysMenuVO detail(Long id) {
        SysMenu entity = sysMenuMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // TODO 字段转换：entity -> vo
        return new SysMenuVO();
    }


    @Override
    public void update(SysMenuDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        SysMenu entity = new SysMenu();
        // TODO 字段赋值：dto -> entity（id 必须赋值）
        sysMenuMapper.updateById(entity);
    }


    @Override
    public PageResult<SysMenuVO> page(SysMenuPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysMenu> lambdaQuery = Wrappers.<SysMenu>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysMenuSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        Page<SysMenu> page = dto.toPage();
        sysMenuMapper.selectPage(page, lambdaQuery);

        return PageResult.of(page, entity -> {
            // TODO 字段转换：entity -> vo
            return new SysMenuVO();
        });
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysMenuMapper.deleteByIdAutoFill(id);
    }


}
