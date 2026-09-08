package com.yeungzhy.yeed.api.export.user;

import com.yeungzhy.yeed.api.export.user.dto.UserExportDTO;
import com.yeungzhy.yeed.api.export.user.dto.UserExportPageDTO;
import com.yeungzhy.yeed.api.feign.config.InternalFeignConfig;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 系统用户导出 内部 Feign 契约（异步导出取数口）
 *
 * <p>消费方：yeed-job（导出执行器）；提供方：yeed-admin
 *
 * <p>两接口配套使用且各司其职：{@link #exportTotal(UserExportPageDTO)} 只统计总命中行数
 * （一条 {@code SELECT COUNT(*)}），作为导出任务的进度分母；{@link #exportPage(UserExportPageDTO)}
 * 只按页取数（带 LIMIT，不带 count），总数在循环开始前一次性取得，逐页重复统计是纯浪费
 *
 * <p>查询条件与分页参数由同一个 {@link UserExportPageDTO} 承载，统一走 POST + JSON body
 *
 * <p>RPC-Style：成功返回裸数据，失败由 {@code InternalErrorDecoder} 解码为
 * {@link com.yeungzhy.yeed.common.core.exception.BizException BizException}，调用方不必判 ApiResult 码
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
     * @param dto 查询条件（username / email / status / 创建时间区间），不能为 null；
     *            分页字段不参与命中判定
     * @return 命中总行数，无命中返回 0；远程失败抛异常（不返回）
     */
    @PostMapping("/export/total")
    Long exportTotal(@Valid @RequestBody UserExportPageDTO dto);


    /**
     * 翻页取当前页用户数据（配合 exportTotal 循环调用直至取完）
     *
     * <p>不做 count：总行数由 {@link #exportTotal} 在循环开始前一次性取得，故只返回本页数据、不带 total
     * 消费方据此判断取完：返回空列表即已无数据（引擎的循环终止条件同时受 total 约束）
     *
     * @param dto 查询条件 + 分页参数（pageNum / pageSize）；查询条件须与 exportTotal 保持一致，
     *            否则翻页过程中取数口径会漂移
     * @return 当前页用户数据，不为 null；远程失败抛异常（不返回）
     */
    @PostMapping("/export/data")
    List<UserExportDTO> exportPage(@Valid @RequestBody UserExportPageDTO dto);



}
