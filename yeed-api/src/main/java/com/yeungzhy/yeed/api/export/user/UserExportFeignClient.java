package com.yeungzhy.yeed.api.export.user;

import com.yeungzhy.yeed.api.export.user.dto.UserExportDTO;
import com.yeungzhy.yeed.api.export.user.dto.UserExportPageDTO;
import com.yeungzhy.yeed.api.feign.config.InternalFeignConfig;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 系统用户导出 内部 Feign 契约（异步导出取数口）
 *
 * <p>消费方：yeed-job（导出执行器）；提供方：yeed-admin
 *
 * <p>两接口配套使用：先 {@link #exportTotal(UserExportPageDTO)} 按查询条件统计总命中行数，
 * 作为导出任务的进度分母；再循环 {@link #exportPage(UserExportPageDTO)} 翻页取数，直至取完。
 *
 * <p>查询条件与分页参数由同一个 {@link UserExportPageDTO} 承载，统一走 POST + JSON body
 *
 * <p>RPC-Style：成功返回裸数据，失败抛
 * {@link com.yeungzhy.yeed.common.core.exception.BizException BizException}（由
 * {@code InternalErrorDecoder} 解码），调用方无需判 ApiResult 码
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@FeignClient(
        name = "yeed-admin",
        contextId = "userExportFeignClient",
        path = "/internal/user",
        configuration = InternalFeignConfig.class
)
public interface UserExportFeignClient {

    /**
     * 统计命中总行数（导出任务进度分母）
     *
     * @param dto 查询条件（username / email / status / 创建时间区间）；分页字段不参与命中判定
     * @return 命中总行数；远程失败抛异常（不返回）
     */
    @PostMapping("/export/total")
    Long exportTotal(@Valid @RequestBody UserExportPageDTO dto);


    /**
     * 翻页取当前页用户数据（配合 exportTotal 循环调用直至取完）
     *
     * @param dto 查询条件 + 分页参数（pageNum / pageSize）；查询条件须与 exportTotal 保持一致，
     *            避免翻页过程中取数口径漂移
     * @return 当前页用户数据（含 total，可据此判断是否已取完）；远程失败抛异常（不返回）
     */
    @PostMapping("/export/data")
    PageResult<UserExportDTO> exportPage(@Valid @RequestBody UserExportPageDTO dto);



}
