package com.yeungzhy.yeed.admin.sys.user.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.common.core.request.IdRequest;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.support.IdenticonUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 系统用户 前端控制器
 *
 * @author yeungzhy
 * @since 2026-08-13 06:51:56
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/sys/user")
public class SysUserController {

    @Resource
    private SysUserService sysUserService;
    @Resource
    private Cache<String, String> identiconCache;

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody SysUserAddDTO dto) {
        return ApiResult.ok(sysUserService.save(dto));
    }


    /**
     * 更新
     *
     * @param dto 入参
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody SysUserUpdateDTO dto) {
        sysUserService.update(dto);
        return ApiResult.ok();
    }


    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/detail")
    public ApiResult<SysUserVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysUserService.detail(id.getId()));
    }


    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysUserVO>> page(@Valid @RequestBody SysUserPageDTO dto) {
        return ApiResult.ok(sysUserService.page(dto));
    }


    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @return 操作结果
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/delete")
    public ApiResult<Boolean> delete(@Valid @RequestBody IdRequest id) {
        sysUserService.delete(id.getId());
        return ApiResult.ok();
    }


    /**
     * 获取用户头像（Identicon 生成式 SVG，半公开接口）
     *
     * <p>半公开指：任意已登录用户可访问任意 id 的头像，不做归属校验。
     * 头像由 {@link IdenticonUtil#generate(String, int, boolean)} 按用户 id 哈希生成，
     * 是确定性纯函数产物，不含业务数据，因此无需权限隔离。
     *
     * <p>两级缓存降低请求开销：
     * <ol>
     *     <li>HTTP 强缓存：响应头 {@code Cache-Control: public, max-age=31536000}（一年），
     *         浏览器/CDN 命中后不再请求后端</li>
     *     <li>进程内 Caffeine 缓存：配置见 {@link com.yeungzhy.yeed.admin.sys.user.config.IdenticonCacheConfig}，
     *         兜底浏览器层未命中（首次访问、清缓存、换终端）时重复的哈希 + SVG 拼接</li>
     * </ol>
     *
     * <p>本接口未做接口级限流，若担心被恶意刷量，可后续补充全局限流。
     *
     * @param id   用户 ID（参与 SVG 生成的唯一变量）
     * @param dark 是否深色模式（true = 深色背景 + 提亮前景色），默认 false
     * @return SVG 响应体（Content-Type: image/svg+xml; charset=UTF-8）
     */
    @GetMapping(value = "/{id}/avatar.svg", produces = "image/svg+xml; charset=UTF-8")
    public ResponseEntity<?> avatar(@PathVariable Long id,
                                    @RequestParam(defaultValue = "false") boolean dark) {

        String key = id + ":" + dark;
        String svg = identiconCache.get(key,
                k -> IdenticonUtil.generate(String.valueOf(id), 10, dark));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml; charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .body(svg);
    }




}
