package com.yeungzhy.yeed.admin.sys.user.controller;

import com.yeungzhy.yeed.admin.sys.user.service.SysUserConvert;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.api.export.user.dto.UserExportDTO;
import com.yeungzhy.yeed.api.export.user.dto.UserExportPageDTO;
import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import com.yeungzhy.yeed.common.web.annotation.InternalApi;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统用户 内部接口控制器（仅供 auth / job 经 Feign 调用）
 *
 * <p>挂载于 {@code /internal/**} 前缀：
 * <ul>
 *   <li>网关层应未定义该前缀的外部路由，避免凭据校验接口被外部直调；</li>
 * </ul>
 *
 * <p>RPC-Style：成功直接返回业务数据（裸返回）；失败抛异常，由
 * {@code InternalApiExceptionHandler} 统一映射为 HTTP 错误码 + ApiResult body，
 * 消费方经 {@code InternalErrorDecoder} 还原为 {@code BizException}。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
@Validated
@InternalApi
@RestController
@RequestMapping("/internal/user")
public class SysUserInternalController {

    @Resource
    private SysUserService sysUserService;
    @Resource
    private SysUserConvert sysUserConvert;


    /**
     * 凭据校验：校验账号密码，校验通过返回登录身份包
     *
     * @param dto 账号 + 密码
     * @return 登录身份包（用户信息 + 角色编码 + 权限标识；菜单树走 user-menus 单独接口）
     */
    @PostMapping("/verify")
    public LoginUserInfo verify(@Valid @RequestBody UserVerifyDTO dto) {
        return sysUserService.verify(dto);
    }

    /**
     * 查询用户可见菜单树（前端侧边栏渲染；登录响应由 auth 组装）
     *
     * @param dto 用户 ID
     * @return 已建树的菜单树节点（仅目录/菜单，不含按钮）
     */
    @PostMapping("/user-menus")
    public List<MenuTreeInfo> userMenus(@Valid @RequestBody UserMenuDTO dto) {
        return sysUserService.listMenusByUserId(dto.getUserId());
    }


    /**
     * 统计命中总行数（导出任务进度分母）
     *
     * <p>只发一条 {@code SELECT COUNT(*)}，与取数口 {@link #exportPage} 共用同一套查询条件，
     * 不会连首页数据一起查出来再丢掉。
     *
     * @param dto 查询条件（username / email / status / 创建时间区间）；分页字段不参与命中判定
     * @return 命中总行数；远程失败抛异常（不返回）
     */
    @PostMapping("/export/total")
    public Long exportTotal(@Valid @RequestBody UserExportPageDTO dto) {
        return sysUserService.count(sysUserConvert.toPageDTO(dto));
    }


    /**
     * 翻页取当前页用户数据（配合 exportTotal 循环调用直至取完）
     *
     * <p>不做 count：总行数由 {@link #exportTotal} 在循环开始前一次性取得，逐页重复统计是纯浪费。
     * 故返回值是本页数据本身，不带 total——没有可信的 total 就不该造一个恒为 0 的字段出来。
     *
     * @param dto 查询条件 + 分页参数（pageNum / pageSize）；查询条件须与 exportTotal 保持一致，
     *            避免翻页过程中取数口径漂移
     * @return 当前页用户数据，空列表表示已取完；远程失败抛异常（不返回）
     */
    @PostMapping("/export/data")
    public List<UserExportDTO> exportPage(@Valid @RequestBody UserExportPageDTO dto) {
        return sysUserService.slice(sysUserConvert.toPageDTO(dto)).stream()
                .map(sysUserConvert::toExportDTO)
                .toList();
    }



}
