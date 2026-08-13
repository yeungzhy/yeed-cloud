package com.yeungzhy.yeed.admin.sys.menu.controller;

import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuMoveDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuPageDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuSaveDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuUpdateDTO;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuPageVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuTreeVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import com.yeungzhy.yeed.common.core.request.IdRequest;
import com.yeungzhy.yeed.common.core.request.IdsRequest;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统菜单权限表 前端控制器
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController {

    @Resource
    private SysMenuService sysMenuService;


    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody SysMenuSaveDTO dto) {
        return ApiResult.ok(sysMenuService.save(dto));
    }


    /**
     * 更新
     *
     * @param dto 入参
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody SysMenuUpdateDTO dto) {
        sysMenuService.update(dto);
        return ApiResult.ok();
    }


    /**
     * 拖拽调整层级（移动菜单到新的父级下）
     * <p>仅更新 parentId；目标父级为 null/0 表示移动到顶级，
     * 目标父级存在性 + 防循环依赖（直接/间接自环）由 Service 校验。
     *
     * @param dto 移动入参（id + 目标 parentId）
     * @return 操作结果
     */
    @PostMapping("/move")
    public ApiResult<Boolean> move(@Valid @RequestBody SysMenuMoveDTO dto) {
        sysMenuService.move(dto);
        return ApiResult.ok();
    }


    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/detail")
    public ApiResult<SysMenuVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysMenuService.detail(id.getId()));
    }


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果（仅业务字段，不含审计字段）
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysMenuPageVO>> page(@Valid @RequestBody SysMenuPageDTO dto) {
        return ApiResult.ok(sysMenuService.page(dto));
    }


    /**
     * 菜单树（菜单管理页 / 角色授权树形选择器共用）
     * <p>返回含按钮权限点的完整树，按 sort 升序；树节点仅承载业务字段。
     * 侧边栏渲染不依赖本接口——侧边栏菜单由用户拥有的角色权限动态组装。
     *
     * @return 完整菜单树
     */
    @PostMapping("/tree")
    public ApiResult<List<SysMenuTreeVO>> tree() {
        return ApiResult.ok(sysMenuService.tree());
    }


    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        sysMenuService.delete(id.getId());
        return ApiResult.ok();
    }


    /**
     * 批量删除（逻辑删除）
     *
     * @param request 主键 ID 集合请求体
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/delete-batch")
    public ApiResult<Boolean> deleteBatch(@Valid @RequestBody IdsRequest request) {
        sysMenuService.delete(request.getIds());
        return ApiResult.ok();
    }


}
