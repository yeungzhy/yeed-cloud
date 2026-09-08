package com.yeungzhy.yeed.admin.sys.user.controller;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPasswordDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserRoleGrantDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.api.export.task.ExportTaskFeignClient;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO;
import com.yeungzhy.yeed.common.core.constant.Constant;
import com.yeungzhy.yeed.common.core.request.IdRequest;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.support.IdenticonUtil;
import com.yeungzhy.yeed.common.core.support.JacksonUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
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
    @Resource
    private ExportTaskFeignClient exportTaskFeignClient;


    /**
     * 新增用户
     *
     * <p>登录名与工号共用同一登录入口，唯一性跨两列判定，只查各自列挡不住「A 的登录名 = B 的工号」
     *
     * @param dto 新增入参，username / password / employeeNo 必填
     * @return 新增记录的主键 ID；账号撞号抛 BizException，不返回失败结果
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/save")
    public ApiResult<Long> save(@Valid @RequestBody SysUserAddDTO dto) {
        return ApiResult.ok(sysUserService.save(dto));
    }


    /**
     * 更新用户资料
     *
     * <p>password 是操作人当前密码、用于验明身份，不是新密码；改密走 change-password
     *
     * @param dto 更新入参，id 与 password 必填；各资料段为空白表示不修改
     * @return 固定成功；用户不存在或密码不正确抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/update")
    public ApiResult<Boolean> update(@Valid @RequestBody SysUserUpdateDTO dto) {
        sysUserService.update(dto);
        return ApiResult.ok();
    }


    /**
     * 修改密码
     *
     * <p>id 为空表示改当前登录用户自己的密码，必须校验原密码；id 非空为管理员重置他人密码，不校验原密码，
     * 能否调用由菜单权限控制
     *
     * @param dto 目标用户 ID + 原密码 + 新密码，newPassword 不能为空
     * @return 固定成功；原密码不符或新旧密码相同抛 BizException
     * @author yeungzhy
     * @since 2026-09-08
     */
    @PostMapping("/change-password")
    public ApiResult<Boolean> changePassword(@Valid @RequestBody SysUserPasswordDTO dto) {
        sysUserService.changePassword(dto);
        return ApiResult.ok();
    }


    /**
     * 用户详情
     *
     * @param id 主键，不能为空
     * @return 详情，手机号/邮箱按掩码规则脱敏；记录不存在抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/detail")
    public ApiResult<SysUserVO> detail(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysUserService.detail(id.getId()));
    }


    /**
     * 分页查询用户
     *
     * @param dto 分页与筛选条件，筛选字段为 null 即不参与过滤；手机/邮箱走盲索引精确匹配
     * @return 分页结果；无命中返回空页而非 null
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/page")
    public ApiResult<PageResult<SysUserVO>> page(@Valid @RequestBody SysUserPageDTO dto) {
        return ApiResult.ok(sysUserService.page(dto));
    }

    /**
     * 发起用户异步导出
     *
     * <p>只创建任务，文件由 job 侧执行；查询条件在此刻序列化成参数快照，后续改列表筛选不影响已在跑的任务
     * 任务创建失败（job 拒绝或不可达）抛异常经全局处理透传，正常返回即表示受理成功
     *
     * @param dto 当前列表的查询入参，直接作为导出条件
     * @return 固定成功；进度与下载走导出任务列表查询
     * @author yeungzhy
     * @since 2026-09-01
     */
    @PostMapping("/export")
    public ApiResult<Boolean> export(@Valid @RequestBody SysUserPageDTO dto) {
        ExportTaskSaveDTO exportTaskSaveDTO = new ExportTaskSaveDTO();
        exportTaskSaveDTO.setExportType(ExportTypeEnum.USER_EXPORT);
        exportTaskSaveDTO.setFileName(StrUtil.format("用户导出{}.xlsx", Constant.COMPACT_DATE_TIME_FORMATTER.format(LocalDateTime.now())));
        exportTaskSaveDTO.setQueryParam(JacksonUtil.toJsonStrIgnoreNull(dto));
        exportTaskFeignClient.save(exportTaskSaveDTO);
        return ApiResult.ok();
    }


    /**
     * 保存用户角色授权（全量覆盖）
     *
     * <p>roleIds 是用户最终的完整角色集合，先清空旧关联再批量写入，传空集等价于解除全部角色
     *
     * @param dto 授权入参，userId 与 roleIds 均不能为空
     * @return 固定成功；用户或角色 ID 不存在抛 BizException
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/grant-roles")
    public ApiResult<Boolean> grantRoles(@Valid @RequestBody SysUserRoleGrantDTO dto) {
        sysUserService.grantRoles(dto);
        return ApiResult.ok();
    }


    /**
     * 查询用户已分配的角色 ID 集合（授权页回显）
     *
     * @param id 用户主键，不能为空
     * @return 已分配角色 ID；未分配时为空列表
     * @author yeungzhy
     * @since 2026-08-13 06:51:56
     */
    @PostMapping("/role-ids")
    public ApiResult<List<Long>> roleIds(@Valid @RequestBody IdRequest id) {
        return ApiResult.ok(sysUserService.listRoleIdsByUser(id.getId()));
    }


    /**
     * 删除用户（逻辑删除）
     *
     * <p>只删用户本身，用户角色关联仍保留：逻辑删除行查不出来，关联不会造成越权，清理留给归档任务
     *
     * @param id 主键，不能为空
     * @return 固定成功；无对应记录不报错
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
     * <p>半公开指：任意已登录用户可访问任意 id 的头像，不做归属校验
     * 头像由 {@link IdenticonUtil#generate(String, int, boolean)} 按用户 id 哈希生成，
     * 是确定性纯函数产物，不含业务数据，因此无需权限隔离
     *
     * <p>两级缓存降低请求开销：
     * <ol>
     *     <li>HTTP 强缓存：响应头 {@code Cache-Control: public, max-age=31536000}（一年），
     *         浏览器/CDN 命中后不再请求后端</li>
     *     <li>进程内 Caffeine 缓存：配置见 {@link com.yeungzhy.yeed.admin.sys.user.config.IdenticonCacheConfig}，
     *         兜底浏览器层未命中（首次访问、清缓存、换终端）时重复的哈希 + SVG 拼接</li>
     * </ol>
     *
     * <p>本接口未做接口级限流，若担心被恶意刷量，可后续补充全局限流
     *
     * @param id   用户 ID，参与 SVG 生成的唯一变量
     * @param dark 是否深色模式（true = 深色背景 + 提亮前景色），默认 false
     * @return SVG 响应体（Content-Type: image/svg+xml; charset=UTF-8）
     * @author yeungzhy
     * @since 2026-08-09
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
