package com.yeungzhy.yeed.admin.sys.user.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.common.support.IdenticonUtil;
import jakarta.annotation.Resource;
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
 * @since 2026-08-09 10:22:12
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/user/sys-user")
public class SysUserController {

    @Resource
    private SysUserService sysUserService;

    @Resource
    private Cache<String, String> identiconCache;


    /**
     * 获取用户头像, 作为半公开接口, 保命做法如下:
     *
     * <p> 鉴权逻辑: 已登录用户，都可以访问任意 id 的头像
     *
     * <p> identicon SVG 为确定性纯函数（按用户 id 哈希生成），因此响应头加 {@code Cache-Control: public, max-age=31536000} 强缓存一年，
     * 浏览器/CDN 命中后不会重复请求。
     *
     * <p> 进程内再用 Caffeine 缓存生成结果，避免每次请求重复做 SHA-256 哈希 + SVG 拼接；
     * 当浏览器层强缓存未命中（首次访问、清缓存、换终端）时，本接口仍能从本地内存纳秒级返回。
     *
     * <p> 有需要可以再添加 项目全局接口限流功能
     *
     * @param id   用户ID
     * @param dark 是否深色模式（true=深色背景+提亮前景色），默认 false
     * @return SVG 响应体 或 302 重定向
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
