package com.yeungzhy.yeed.openapi.app.controller;

import com.yeungzhy.yeed.common.core.request.IdRequest;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppDTO;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppPageDTO;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppSaveDTO;
import com.yeungzhy.yeed.openapi.app.service.OpenapiAppService;
import com.yeungzhy.yeed.openapi.app.vo.OpenapiAppVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenApi 接入应用 后台管理接口
 *
 * <p>表 yeed_openapi_app 的管理面：网关按 /yeed-openapi/sys/app/** 转发（StripPrefix=1），
 * 菜单按钮 path 需填全路径 /yeed-openapi/sys/app/page，perms 派生 yeed-openapi:sys:app:page
 *
 * @author yeungzhy
 * @since 2026-09-09
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sys/app")
public class OpenapiAppController {

    @Resource
    private OpenapiAppService openapiAppService;


    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-09-09
     */
    @PostMapping("/save")
    public ApiResult<OpenapiAppVO> save(@Valid @RequestBody OpenapiAppSaveDTO dto) {
        return ApiResult.ok(openapiAppService.save(dto));
    }


    /**
     * 更新
     *
     * @param dto 入参
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-09
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody OpenapiAppDTO dto) {
        openapiAppService.update(dto);
        return ApiResult.ok();
    }


    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-09-09
     */
    @PostMapping("/detail")
    public ApiResult<OpenapiAppVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(openapiAppService.detail(id.getId()));
    }


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-09-09
     */
    @PostMapping("/page")
    public ApiResult<PageResult<OpenapiAppVO>> page(@Valid @RequestBody OpenapiAppPageDTO dto) {
        return ApiResult.ok(openapiAppService.page(dto));
    }


    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-09-09
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        openapiAppService.delete(id.getId());
        return ApiResult.ok();
    }


}
