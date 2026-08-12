package com.yeungzhy.yeed.common.core.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页返回体（与 ORM 框架解耦）
 *
 * <p>仅通过 {@link #empty()} 或 {@link #of(long, long, long, List)} 构造；
 * 如需从 MyBatis-Plus 的 IPage 转换，请使用 common-data 的 {@code MybatisPageConverters.toPageResult}，
 * 切勿在此处引入 ORM 类型或提供"records 元素变换"方法（变换由 MapStruct 生成的 XxxConvert 在组装前完成）。
 */
@Data
@Accessors(chain = true)
public class PageResult<T> {

    /** 当前页码 */
    private Long pageNum;

    /** 每页条数 */
    private Long pageSize;

    /** 总记录数 */
    private Long total;

    /** 当前页数据 */
    private List<T> records;


    public static <T> PageResult<T> empty() {
        return new PageResult<T>()
                .setPageNum(1L)
                .setPageSize(10L)
                .setTotal(0L)
                .setRecords(Collections.emptyList());
    }

    /**
     * 从原始分页字段构造（不耦合任何 ORM 框架）
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param total    总记录数
     * @param records  当前页数据
     */
    public static <T> PageResult<T> of(long pageNum, long pageSize, long total, List<T> records) {
        return new PageResult<T>()
                .setPageNum(pageNum)
                .setPageSize(pageSize)
                .setTotal(total)
                .setRecords(records == null ? Collections.emptyList() : records);
    }

}
