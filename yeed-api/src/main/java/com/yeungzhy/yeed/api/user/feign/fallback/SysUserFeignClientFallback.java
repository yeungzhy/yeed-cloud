package com.yeungzhy.yeed.api.user.feign.fallback;

import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.api.user.feign.SysUserFeignClient;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;

import java.util.List;

/**
 * {@link SysUserFeignClient} 兜底降级实现：admin 不可达、调用超时或抛出未处理异常时，
 * 由 Feign 转入本类返回统一降级响应，避免异常冒泡到 auth 业务层。
 *
 * <p>触发边界：仅远程调用失败进入兜底。业务失败（账号不存在、密码错误）由 admin 正常返回
 * {@code ApiResult.error} 走响应链路，不经过本类——两者必须区分对待，否则会把"密码错"误判成"服务挂了"。
 *
 * <p>降级响应固定为 {@code REMOTE_SERVICE_ERROR}（code=1008，属领域语义组），HTTP 状态码仍是 200：
 * 调用方必须判 {@code ApiResult.getCode()}，不能按 HTTP 状态码判断成败。
 *
 * @author yeungzhy
 * @since 2026-08-09
 * @see SysUserFeignClientFallbackFactory
 */
public class SysUserFeignClientFallback implements SysUserFeignClient {

    /**
     * 兜底：登录校验请求未能送达 admin
     *
     * <p>结果为拒绝登录（fail-closed）：admin 不可达期间全员无法登录，是刻意的安全取舍——
     * 宁可拒绝也不能在校验未执行时放行。
     *
     * @param dto 原始入参，降级路径不读取
     * @return 固定 {@code REMOTE_SERVICE_ERROR} 降级响应，data 恒为 null
     */
    @Override
    public ApiResult<LoginUserInfo> verify(UserVerifyDTO dto) {
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

    /**
     * 兜底：菜单查询未能送达 admin
     *
     * <p>{@link #verify(UserVerifyDTO)} 已成功时才会走到这里，表现为"能登录但菜单为空"；
     * 调用方应降级为空菜单并提示，不要把登录流程整体判失败。
     *
     * @param dto 原始入参，降级路径不读取
     * @return 固定 {@code REMOTE_SERVICE_ERROR} 降级响应，data 恒为 null（不是空集合）
     */
    @Override
    public ApiResult<List<MenuTreeInfo>> userMenus(UserMenuDTO dto) {
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

}
