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
     * 新增菜单
     *
     * <p>网关按 {@code Path=/微服务名/**} 前缀路由（如 yeed-admin、yeed-auth），
     * 因此按钮类菜单的 path 入参要带上微服务名，否则请求根本到不了本服务
     *
     * @param dto 新增入参，menuName / menuType / parentId 必填；按钮类型还须带 path
     * @return 新增记录的主键 ID；校验失败抛 BizException，不返回失败结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody SysMenuSaveDTO dto) {
        return ApiResult.ok(sysMenuService.save(dto));
    }


    /**
     * 更新菜单
     *
     * @param dto 更新入参，id 不能为空
     * @return 固定成功；记录不存在或目标父级非法抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody SysMenuUpdateDTO dto) {
        sysMenuService.update(dto);
        return ApiResult.ok();
    }


    /**
     * 拖拽调整层级（只改 parentId）
     *
     * <p>目标父级为 0 表示移到顶级（项目约定不存在 null）；父级存在性与循环依赖由 Service 校验，
     * 后者会把「目标父级是自己后代」的间接自环一并拦掉
     *
     * @param dto 移动入参，id 与 parentId 均不能为空
     * @return 固定成功；父级非法抛 BizException
     * @author yeungzhy
     * @since 2026-08-13
     */
    @PostMapping("/move")
    public ApiResult<Boolean> move(@Valid @RequestBody SysMenuMoveDTO dto) {
        sysMenuService.move(dto);
        return ApiResult.ok();
    }


    /**
     * 菜单详情
     *
     * @param id 主键，不能为空
     * @return 详情，含审计字段与 version；记录不存在抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/detail")
    public ApiResult<SysMenuVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysMenuService.detail(id.getId()));
    }


    /**
     * 分页查询菜单
     *
     * @param dto 分页与筛选条件，筛选字段为 null 即不参与过滤
     * @return 分页结果，出参不含审计字段；无命中返回空页而非 null
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysMenuPageVO>> page(@Valid @RequestBody SysMenuPageDTO dto) {
        return ApiResult.ok(sysMenuService.page(dto));
    }


    /**
     * 菜单树（菜单管理页 / 角色授权树形选择器共用）
     *
     * <p>返回含按钮权限点的完整树，按 sort 升序，节点仅承载业务字段；侧边栏渲染不走本接口，
     * 侧边栏菜单由当前用户拥有的角色权限动态组装
     *
     * @return 完整菜单树；无菜单时为空列表
     * @author yeungzhy
     * @since 2026-08-13
     */
    @PostMapping("/tree")
    public ApiResult<List<SysMenuTreeVO>> tree() {
        return ApiResult.ok(sysMenuService.tree());
    }


    /**
     * 删除菜单（逻辑删除）
     *
     * @param id 主键，不能为空；存在子菜单时拒绝删除
     * @return 固定成功；不满足删除条件抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        sysMenuService.delete(id.getId());
        return ApiResult.ok();
    }


    /**
     * 批量删除菜单（逻辑删除）
     *
     * @param request 主键集合，不能为空；这批菜单任意一个有子菜单即整体拒绝
     * @return 固定成功；不满足删除条件抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:55:30
     */
    @PostMapping("/delete-batch")
    public ApiResult<Boolean> deleteBatch(@Valid @RequestBody IdsRequest request) {
        sysMenuService.delete(request.getIds());
        return ApiResult.ok();
    }


}
