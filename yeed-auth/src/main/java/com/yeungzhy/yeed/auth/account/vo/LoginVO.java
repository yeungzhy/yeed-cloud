package com.yeungzhy.yeed.auth.account.vo;

import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 登录响应结果
 *
 * <p>前端登录成功后：
 * <ul>
 *   <li>取 {@link #token} 存入本地，后续每个请求在请求头中携带，由网关统一鉴权；</li>
 *   <li>取 {@link #loginUser} 存入全局状态，供展示当前登录人与按钮级权限指令（{@code v-permission}）使用；</li>
 *   <li>取 {@link #menus} 渲染侧边栏菜单树与前端路由。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Data
@Accessors(chain = true)
public class LoginVO {

    /** 令牌前缀 */
    private String tokenPrefix;

    /** 登录令牌，前端需在后续请求头中携带（头名为 Authorization） */
    private String token;

    /** 登录身份包（用户信息 + 角色编码 + 权限标识；不含菜单树） */
    private LoginUserInfo loginUser;

    /** 用户可见菜单树（仅目录/菜单节点，已建树），供前端侧边栏/路由渲染 */
    private List<MenuTreeInfo> menus;

}
