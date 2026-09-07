package com.yeungzhy.yeed.job.export.biz.user;

import com.alibaba.excel.annotation.ExcelProperty;
import com.yeungzhy.yeed.api.export.user.dto.UserExportDTO;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户导出的 Excel 行对象：字段即列，列名与列序由 {@code @ExcelProperty} 声明
 *
 * <p>当前尚未定义字段——引擎反射本类生成表头，字段为空时导出的是只有表头的空文件。
 *
 * @author yeungzhy
 * @since 2026-08-23
 * @see UserExporter
 */
@Data
public class UserExportRow {

    /** 真实姓名(用于前台展示) */
    @ExcelProperty(value = "真实姓名")
    private String realName;
    /** 系统登录名 */
    @ExcelProperty(value = "系统登录名")
    private String username;
    /** 工号 */
    @ExcelProperty(value = "工号")
    private String employeeNo;
    /** 邮箱 */
    @ExcelProperty(value = "邮箱")
    private String email;
    /** 状态：0-禁用，1-启用 */
    @ExcelProperty(value = "状态")
    private EnableStatusEnum status;
    /** 创建时间 */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;


    public static UserExportRow from(UserExportDTO userExportDTO) {
        UserExportRow userExportRow = new UserExportRow();
        userExportRow.setRealName(userExportDTO.getRealName());
        userExportRow.setUsername(userExportDTO.getUsername());
        userExportRow.setEmployeeNo(userExportDTO.getEmployeeNo());
        userExportRow.setEmail(userExportDTO.getEmail());
        userExportRow.setStatus(userExportDTO.getStatus());
        userExportRow.setCreateTime(userExportDTO.getCreateTime());
        return userExportRow;
    }

}
