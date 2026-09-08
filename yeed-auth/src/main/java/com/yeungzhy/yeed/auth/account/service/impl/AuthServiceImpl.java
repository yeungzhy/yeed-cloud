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
 * <p>凭据校验委托 admin：密码散列只有 admin 持有，这里拿到 {@link LoginUserInfo} 即视为凭据通过
 *
 * <p>登录态分两处落，边界是「要不要随每次请求被读出来」：
 * <ul>
 *   <li>Sa-Token session 只放 {@link LoginUserInfo}（含角色编码与权限标识），网关鉴权每次都要读
 *   <li>菜单树只回前端渲染，不进 session；它是整棵树，塞进会话会放大每次鉴权的反序列化成本
 * </ul>
 *
 * <p>异常透传：admin 内部端点是 RPC-Style（裸数据 + 异常），凭据错误由 admin 抛异常映射为 HTTP 错误码，
 * auth 侧 {@code InternalErrorDecoder} 还原成 {@code BizException(码, 话术)} 中断本方法，
 * 再由 ExternalApiExceptionHandler 原样透给前端，不在本类改写话术
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
        // 失败（账号不存在 / 密码错误）由 InternalErrorDecoder 还原为 BizException，话术直接到前端
        LoginUserInfo loginUser = sysUserFeignClient.verify(
                new UserVerifyDTO()
                        .setAccount(dto.getAccount())
                        .setPassword(dto.getPassword())
        );
        BizAssert.notNull(loginUser, "登录失败");

        StpUtil.login(loginUser.getUserId());
        StpUtil.getSession().set(SessionKeys.LOGIN_USER, loginUser);

        log.info("用户登录成功 userId={} username={}", loginUser.getUserId(), loginUser.getUsername());

        List<MenuTreeInfo> menus = listMenus(loginUser.getUserId());

        return new LoginVO()
                .setTokenPrefix(SaManager.getConfig().getTokenPrefix() + " ")
                .setToken(StpUtil.getTokenInfo().getTokenValue())
                .setLoginUser(loginUser)
                .setMenus(menus);
    }

    /**
     * 拉取用户可见菜单树
     *
     * <p>菜单树只供前端渲染，拉取失败时按空菜单降级并打日志，不阻断登录：身份包已写好，前端刷新即可重试
     *
     * <p>只 catch 业务异常与 Feign 异常，范围可控；范围外的异常照常上抛，不被降级语义掩盖
     *
     * @param userId 用户主键，不能为 null
     * @return 已建树的菜单树节点；远程失败时为空列表，不为 null
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
