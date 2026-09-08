package com.yeungzhy.yeed.admin.sys.user.dto;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统用户 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
public class SysUserDTO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 系统登录名（登录入口，未删用户中唯一） */
    private String username;
    /** 密码（Argon2id 单向散列） */
    private String password;
    /** 真实姓名（用于前台展示） */
    private String realName;
    /** 工号（登录入口，未删用户中唯一） */
    private String employeeNo;
    /** 手机号 */
    private String phone;
    /** 手机号盲索引 */
    private String phoneBidx;
    /** 邮箱 */
    private String email;
    /** 邮箱盲索引 */
    private String emailBidx;
    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
    /** 状态：0-禁用，1-启用 */
    private EnableStatusEnum status;

    // ================== 审计字段 ==================
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新人 */
    private Long updateBy;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 删除人 */
    private Long deleteBy;
    /** 逻辑删除，0-未删，时间戳-已删 */
    private Long deleteTime;
    /** 乐观锁 */
    private Integer version;
    /** 扩展信息 */
    private Map<String, Object> extra;

}
