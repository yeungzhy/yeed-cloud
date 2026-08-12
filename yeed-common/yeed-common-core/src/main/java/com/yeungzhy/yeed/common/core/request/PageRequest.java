package com.yeungzhy.yeed.common.core.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 通用分页请求体
 *
 * <p>支持单字段排序（{@link #orderField} + {@link #isAsc}）与多字段排序（{@link #orders}），
 * 不在后端白名单内的排序字段会被静默忽略（由 common-data 的 BaseSorts 校验）。
 */
@Data
@Accessors(chain = true)
public class PageRequest {

    /** 页码 */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须在 1-3000 之间")
    @Max(value = 3000, message = "页码必须在 1-3000 之间")
    private Integer pageNum = 1;

    /** 每页条数 */
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须在 1-2000 之间")
    @Max(value = 2000, message = "每页条数必须在 1-2000 之间")
    private Integer pageSize = 10;

    /* ============ 排序：两套接口并存 ============ */

    /**
     * 单字段排序 - 字段名（Java 属性名，驼峰）
     * <p>便捷接口，简单场景一行搞定；多字段场景用 {@link #orders}
     * <p>不在后端白名单内的字段会被静默忽略（见 {@code BaseSorts}）
     */
    private String orderField;

    /**
     * 单字段排序 - 是否升序
     * <p><b>null 默认降序</b>（业务惯例：最新记录在前）
     * <p>只有显式传 true 才升序；false / null 都是降序
     */
    private Boolean isAsc;

    /**
     * 多字段排序（顺序敏感，按列表顺序依次 ORDER BY）
     * <p>例：先按状态升序，再按创建时间降序（最新的在前）
     * <pre>[
     *   {"field": "status",     "isAsc": true},
     *   {"field": "createTime", "isAsc": false}
     * ]</pre>
     * <p>执行顺序规则：<b>先 {@link #orderField} 单字段 → 再多字段列表</b>
     * <p>各条目 isAsc 为 null → <b>默认降序</b>；不在白名单内的字段静默忽略
     */
    private List<OrderItem> orders;

    /**
     * 多字段排序条目
     */
    @Data
    @Accessors(chain = true)
    public static class OrderItem {
        /** Java 属性名（驼峰）；不在白名单内会被静默忽略 */
        private String field;
        /** 是否升序；<b>null 默认降序</b> */
        private Boolean isAsc;
    }

}
