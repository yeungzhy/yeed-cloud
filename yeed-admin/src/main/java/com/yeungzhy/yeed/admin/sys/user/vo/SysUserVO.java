package com.yeungzhy.yeed.admin.sys.user.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.sensitive.Sensitive;
import com.yeungzhy.yeed.common.core.sensitive.SensitiveType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统用户 VO（详情 / 分页列表出参）
 *
 * <p>不含 password；phone / email 由 {@code @Sensitive} 在序列化时按掩码规则脱敏
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysUserVO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 系统登录名（登录入口，未删用户中唯一） */
    private String username;
    /** 真实姓名（用于前台展示） */
    private String realName;
    /** 工号（登录入口，未删用户中唯一） */
    private String employeeNo;
    /** 手机号 */
    @Sensitive(type = SensitiveType.PHONE)
    private String phone;
    /** 邮箱 */
    @Sensitive(type = SensitiveType.EMAIL)
    private String email;
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
