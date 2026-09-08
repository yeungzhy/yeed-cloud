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
     * 新增角色
     *
     * <p>roleCode 不能命中内置角色编码白名单，与既有角色重复同样拒绝
     *
     * @param dto 新增入参，roleName / roleCode 均不能为空
     * @return 新增记录的主键 ID；校验失败抛 BizException，不返回失败结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody SysRoleDTO dto) {
        return ApiResult.ok(sysRoleService.save(dto));
    }


    /**
     * 更新角色
     *
     * <p>内置角色禁改 roleCode：内置身份由编码白名单判定，改了编码等于绕过内置保护
     *
     * @param dto 更新入参，id 不能为空
     * @return 固定成功；记录不存在或校验失败抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody SysRoleDTO dto) {
        sysRoleService.update(dto);
        return ApiResult.ok();
    }

    /**
     * 启停角色，内置角色拒绝变更
     *
     * @param dto 主键与目标状态，两者均不能为空
     * @return 固定成功；内置角色或记录不存在抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 18:29
     */
    @PostMapping("/update-status")
    public ApiResult<Boolean> updateStatus(@Valid @RequestBody StatusRequest dto) {
        sysRoleService.updateStatus(dto);
        return ApiResult.ok();
    }


    /**
     * 角色详情
     *
     * @param id 主键，不能为空
     * @return 详情；记录不存在抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/detail")
    public ApiResult<SysRoleVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysRoleService.detail(id.getId()));
    }


    /**
     * 分页查询角色
     *
     * @param dto 分页与筛选条件，三个筛选字段都可为空（为空即不参与过滤）
     * @return 分页结果，出参不含审计字段；无命中返回空页而非 null
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysRolePageVO>> page(@Valid @RequestBody SysRolePageDTO dto) {
        return ApiResult.ok(sysRoleService.page(dto));
    }


    /**
     * 保存角色菜单授权（全量覆盖）
     *
     * <p>menuIds 是角色最终的完整权限集合（含按钮权限点），先清空旧关联再批量写入，
     * 祖先节点由服务端沿 parentId 补全，前端漏传也不会断链
     *
     * @param dto 授权入参，roleId 与 menuIds 均不能为空
     * @return 固定成功；角色不存在或菜单 ID 有无效值抛 BizException
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
     * @param id 角色主键，不能为空
     * @return 已授权菜单 ID；无授权时为空列表
     * @author yeungzhy
     * @since 2026-08-13 18:29
     */
    @PostMapping("/menu-ids")
    public ApiResult<List<Long>> menuIds(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysRoleService.listMenuIdsByRole(id.getId()));
    }


    /**
     * 删除角色（逻辑删除）
     *
     * <p>内置角色与已绑定用户的角色拒绝删除，通过校验后级联清理角色菜单关联
     *
     * @param id 主键，不能为空
     * @return 固定成功；不满足删除条件抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        sysRoleService.delete(id.getId());
        return ApiResult.ok();
    }


    /**
     * 批量删除角色（逻辑删除）
     *
     * <p>整批校验后再删：任一内置角色或已绑定用户即整体拒绝，不做部分删除
     *
     * @param request 主键集合，不能为空
     * @return 固定成功；不满足删除条件抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/delete-batch")
    public ApiResult<Boolean> deleteBatch(@Valid @RequestBody IdsRequest request) {
        sysRoleService.delete(request.getIds());
        return ApiResult.ok();
    }



}
