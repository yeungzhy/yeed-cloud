package com.yeungzhy.yeed.admin.sys.role.controller;

import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleMenuGrantDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleService;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRolePageVO;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
import com.yeungzhy.yeed.common.core.request.IdRequest;
import com.yeungzhy.yeed.common.core.request.IdsRequest;
import com.yeungzhy.yeed.common.core.request.StatusRequest;
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
 * 系统角色 前端控制器
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sys/role")
public class SysRoleController {

    @Resource
    private SysRoleService sysRoleService;


    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody SysRoleDTO dto) {
        return ApiResult.ok(sysRoleService.save(dto));
    }


    /**
     * 更新
     *
     * @param dto 入参
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody SysRoleDTO dto) {
        sysRoleService.update(dto);
        return ApiResult.ok();
    }

    /**
     * 更新状态
     *
     * @param dto 入参
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 18:29
     */
    @PostMapping("/update-status")
    public ApiResult<Boolean> updateStatus(@Valid @RequestBody StatusRequest dto) {
        sysRoleService.updateStatus(dto);
        return ApiResult.ok();
    }


    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/detail")
    public ApiResult<SysRoleVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysRoleService.detail(id.getId()));
    }


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果（仅业务字段，不含审计字段）
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysRolePageVO>> page(@Valid @RequestBody SysRolePageDTO dto) {
        return ApiResult.ok(sysRoleService.page(dto));
    }


    /**
     * 保存角色菜单授权（全量覆盖）
     * <p>menuIds 为该角色最终的完整权限集合（含按钮权限点）：先清空旧关联，再批量写入新关联。
     *
     * @param dto 授权入参（角色ID + 菜单ID集合）
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 18:29
     */
    @PostMapping("/grant-menus")
    public ApiResult<Boolean> grantMenus(@Valid @RequestBody SysRoleMenuGrantDTO dto) {
        sysRoleService.grantMenus(dto);
        return ApiResult.ok();
    }


    /**
     * 查询角色已授权的菜单 ID 集合（授权页回显）
     *
     * @param id 角色 ID
     * @return 已授权菜单 ID 集合
     * @author yeungzhy
     * @since 2026-08-13 18:29
     */
    @PostMapping("/menu-ids")
    public ApiResult<List<Long>> menuIds(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysRoleService.listMenuIdsByRole(id.getId()));
    }


    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        sysRoleService.delete(id.getId());
        return ApiResult.ok();
    }


    /**
     * 批量删除（逻辑删除）
     *
     * @param request 主键 ID 集合请求体
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/delete-batch")
    public ApiResult<Boolean> deleteBatch(@Valid @RequestBody IdsRequest request) {
        sysRoleService.delete(request.getIds());
        return ApiResult.ok();
    }



}
