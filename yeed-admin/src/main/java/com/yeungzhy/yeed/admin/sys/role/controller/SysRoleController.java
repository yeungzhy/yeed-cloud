package com.yeungzhy.yeed.admin.sys.role.controller;

import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleService;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
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
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysRoleVO>> page(@Valid @RequestBody SysRolePageDTO dto) {
        return ApiResult.ok(sysRoleService.page(dto));
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


    /**
     * 回收站分页（包含已逻辑删除的数据）
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/recycle/page")
    public ApiResult<PageResult<SysRoleVO>> pageWithDeleted(@Valid @RequestBody SysRolePageDTO dto) {
        return ApiResult.ok(sysRoleService.pageWithDeleted(dto));
    }


    /**
     * 恢复已逻辑删除的数据
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/restore")
    public ApiResult<Boolean> restore(@Valid @RequestBody IdRequest id) {
        sysRoleService.restoreById(id.getId());
        return ApiResult.ok();
    }


    /**
     * 物理删除（真 DELETE，不可恢复，仅用于"回收站彻底删除"等场景）
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:55:14
     */
    @PostMapping("/physical-delete")
    public ApiResult<Boolean> physicalDelete(@Valid @RequestBody IdRequest id) {
        sysRoleService.physicalDeleteById(id.getId());
        return ApiResult.ok();
    }


}
