package com.yeungzhy.yeed.admin.sys.user.vo;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统用户 VO（返回出参）
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Data
@Accessors(chain = true)
public class SysUserVO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 真实姓名(用于前台展示) */
    private String realName;
    /** 系统登录名 */
    private String username;
    /** 工号 */
    private String employeeNo;
    /** 密码 */
    private String password;
    /** 邮箱 */
    private String email;
    /** 邮箱盲索引 */
    private String emailBidx;
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
    /** 逻辑删除,0-未删,时间戳-已删 */
    private Long deleteTime;
    /** 乐观锁 */
    private Integer version;
    /** 扩展信息 */
    private Map<String, Object> extra;

}
