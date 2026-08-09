package com.yeungzhy.yeed.admin.sys.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 用户角色关联表
 *
 * @author yeungzhy
 * @since 2026-08-09 10:25:13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "yeed_sys_user_role")
public class SysUserRole {

    /** 雪花ID主键 */
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 角色ID */
    private Long roleId;

}
