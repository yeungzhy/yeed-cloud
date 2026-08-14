package com.yeungzhy.yeed.common.core.security;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 登录身份包（服务端会话数据）
 *
 * <p>随登录写入 Sa-Token session（Redis），供下游鉴权与当前登录人读取：
 * {@code SaPermissionImpl} 取 roleCodes/perms、{@link LoginUserHelper} 暴露当前用户。
 * <p><b>职责边界</b>：本类只承载"服务端鉴权所需"数据；前端渲染用的菜单树
 * （{@link MenuTreeInfo}）不进会话，由登录响应/用户菜单接口单独返回。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Data
@Accessors(chain = true)
public class LoginUserInfo {

    /** 用户主键 */
    private Long userId;
    /** 真实姓名（前台展示） */
    private String realName;
    /** 系统登录名 */
    private String username;
    /** 工号 */
    private String employeeNo;
    /** 账号状态 */
    private EnableStatusEnum status;
    /** 角色编码集合 */
    private List<String> roleCodes;
    /** 有权限的菜单权限码集合 */
    private List<String> perms;

}
