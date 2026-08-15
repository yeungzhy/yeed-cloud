package com.yeungzhy.yeed.auth.account.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.api.user.feign.SysUserFeignClient;
import com.yeungzhy.yeed.auth.account.dto.LoginDTO;
import com.yeungzhy.yeed.auth.account.service.AuthService;
import com.yeungzhy.yeed.auth.account.vo.LoginVO;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import com.yeungzhy.yeed.common.core.security.SessionKeys;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务实现
 *
 * <p>登录流程：
 * <ol>
 *   <li>经 Feign 调 admin 的 {@code /internal/user/verify} 校验账号密码，返回 {@link LoginUserInfo}（会话身份包）；</li>
 *   <li>{@link StpUtil#login(Object)} 签发 token（UUID，存共享 Redis）；</li>
 *   <li>将 {@link LoginUserInfo} 写入 Sa-Token session，供下游 {@code SaPermissionImpl} 读取权限/角色；</li>
 *   <li>经 Feign 调 admin 的 {@code /internal/user/user-menus} 拉取菜单树（前端渲染数据，不进 session）；</li>
 *   <li>回前端 token + 登录身份包 + 菜单树。</li>
 * </ol>
 *
 * <p><b>异常透传</b>：admin 侧凭据校验失败（账号不存在/密码错误）时，由 admin 的 GlobalExceptionHandler
 * 包成 {@code ApiResult.error(msg)} 返回（HTTP 200）；本类断言 {@code result.isOk()} 失败时抛
 * {@code BizException(msg)}，由 auth 侧 GlobalExceptionHandler 原样透传给前端。
 *
 * <p><b>菜单树降级</b>：user-menus 拉取失败（远程异常走 Fallback）时按空菜单降级并打 warn，
 * 不阻断登录——菜单树仅供前端渲染，会话身份包已成功写入。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private SysUserFeignClient sysUserFeignClient;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. Feign 调 admin 校验凭据
        ApiResult<LoginUserInfo> result = sysUserFeignClient.verify(
                new UserVerifyDTO()
                        .setAccount(dto.getAccount())
                        .setPassword(dto.getPassword())
        );
        // admin 凭据校验失败时 result.isOk()=false，desc 透传给前端
        BizAssert.isTrue(result.succeed(), result.getDesc());

        LoginUserInfo loginUser = result.getData();
        BizAssert.notNull(loginUser, "登录失败");

        // 2. 签发 token + 写 session
        StpUtil.login(loginUser.getUserId());
        StpUtil.getSession().set(SessionKeys.LOGIN_USER, loginUser);

        log.info("用户登录成功 userId={} username={}", loginUser.getUserId(), loginUser.getUsername());

        // 3. 拉取用户菜单树（前端渲染数据，不进 session；失败降级为空，不阻断登录）
        List<MenuTreeInfo> menus = listMenus(loginUser.getUserId());

        // 4. 组装响应
        return new LoginVO()
                .setTokenPrefix(SaManager.getConfig().getTokenPrefix() + " ")
                .setToken(StpUtil.getTokenInfo().getTokenValue())
                .setLoginUser(loginUser)
                .setMenus(menus);
    }

    /**
     * 拉取用户可见菜单树
     *
     * <p>菜单树仅供前端侧边栏/路由渲染，拉取失败（远程异常走 Fallback）时按空菜单降级
     * 并打 warn，不阻断登录——会话身份包已成功写入，前端可刷新页面重试。
     *
     * @param userId 用户 ID
     * @return 已建树的菜单树节点（失败时为空列表）
     */
    private List<MenuTreeInfo> listMenus(Long userId) {
        ApiResult<List<MenuTreeInfo>> result = sysUserFeignClient.userMenus(new UserMenuDTO().setUserId(userId));
        if (!result.succeed() || result.getData() == null) {
            log.warn("获取用户菜单树失败，按空菜单降级 userId={} desc={}", userId, result.getDesc());
            return List.of();
        }
        return result.getData();
    }

}
