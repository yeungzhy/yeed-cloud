package com.yeungzhy.yeed.admin.sys.role.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统角色 VO（返回出参）
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:37
 */
@Data
@Accessors(chain = true)
public class SysRoleVO {

    // ================== 主键 ==================
    /** 雪花ID主键 */
    private Long id;

    // ================== 业务字段 ==================
    /** 角色名称 */
    private String roleName;
    /** 角色编码（业务唯一标识） */
    private String roleCode;
    /** 角色描述 */
    private String description;
    /** 状态：0-禁用，1-启用 */
    private Integer status;

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
