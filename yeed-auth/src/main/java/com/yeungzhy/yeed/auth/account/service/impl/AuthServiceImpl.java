package com.yeungzhy.yeed.auth.account.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.api.user.SysUserFeignClient;
import com.yeungzhy.yeed.auth.account.dto.LoginDTO;
import com.yeungzhy.yeed.auth.account.service.AuthService;
import com.yeungzhy.yeed.auth.account.vo.LoginVO;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.exception.BizException;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import com.yeungzhy.yeed.common.core.security.SessionKeys;
import feign.FeignException;
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
 * <p><b>异常透传</b>：admin 内部端点为裸数据契约（终态 A）——凭据校验失败（账号不存在/密码错误）由
 * admin 抛异常映射为 HTTP 错误码，auth 侧 {@code InternalErrorDecoder} 还原为
 * {@code BizException(码, 话术)} 直接中断本方法，auth 的 ExternalApiExceptionHandler 原样透传前端。
 *
 * <p><b>菜单树降级</b>：user-menus 拉取失败（业务失败/连接失败）时按空菜单降级并打 warn，
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
        // 1. Feign 调 admin 校验凭据：成功返回身份包；失败（账号/密码错误等）抛 BizException 中断，话术透传前端
        LoginUserInfo loginUser = sysUserFeignClient.verify(
                new UserVerifyDTO()
                        .setAccount(dto.getAccount())
                        .setPassword(dto.getPassword())
        );
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
     * <p>菜单树仅供前端侧边栏/路由渲染，拉取失败时按空菜单降级并打 warn，不阻断登录——
     * 会话身份包已成功写入，前端可刷新页面重试。捕获范围收窄为"远程失败"两类
     * （业务失败 {@link BizException} / 连接失败 {@link FeignException}），
     * 代码缺陷（NPE 等）仍正常上抛，不被降级语义掩盖。
     *
     * @param userId 用户 ID
     * @return 已建树的菜单树节点（远程失败时为空列表）
     */
    private List<MenuTreeInfo> listMenus(Long userId) {
        try {
            return sysUserFeignClient.userMenus(new UserMenuDTO().setUserId(userId));
        } catch (BizException | FeignException e) {
            log.error("获取用户菜单树失败，按空菜单降级 userId={} err={}", userId, e.getMessage());
            return List.of();
        }
    }

}
