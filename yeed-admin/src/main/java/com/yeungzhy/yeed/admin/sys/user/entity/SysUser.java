package com.yeungzhy.yeed.admin.sys.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

/**
 * 系统用户
 *
 * @author yeungzhy
 * @since 2026-08-09 11:53:04
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_user", autoResultMap = true)
public class SysUser extends BaseEntity {

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
    private Integer status;

}
