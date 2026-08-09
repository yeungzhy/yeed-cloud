package com.yeungzhy.yeed.admin.sys.menu.controller;

import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuPageDTO;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import com.yeungzhy.yeed.common.request.IdRequest;
import com.yeungzhy.yeed.common.result.ApiResult;
import com.yeungzhy.yeed.common.result.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统菜单权限表 前端控制器
 *
 * @author yeungzhy
 * @since 2026-08-09 10:26:00
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
     * @since 2026-08-09 16:28:56
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody SysMenuDTO dto) {
        return ApiResult.ok(sysMenuService.save(dto));
    }


    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    @PostMapping("/detail")
    public ApiResult<SysMenuVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysMenuService.detail(id.getId()));
    }


    /**
     * 更新
     *
     * @param dto 入参
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody SysMenuDTO dto) {
        sysMenuService.update(dto);
        return ApiResult.ok();
    }


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysMenuVO>> page(@Valid @RequestBody SysMenuPageDTO dto) {
        return ApiResult.ok(sysMenuService.page(dto));
    }


    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-09 16:28:56
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        sysMenuService.delete(id.getId());
        return ApiResult.ok();
    }


}
