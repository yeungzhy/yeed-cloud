package com.yeungzhy.yeed.common.core.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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

    /**
     * 把 PageResult&lt;ENTITY&gt; 转成 PageResult&lt;VO&gt;，常用：entity 列表转成 vo 列表
     * <p> 纯 POJO 转换，不耦合任何 ORM 框架；如需从 MyBatis-Plus 的 IPage 转换，
     * 请使用 common-data 中的 {@code MybatisPageConverters.toPageResult}
     *
     * @param source 原始分页结果
     * @param mapper entity → vo 转换函数
     */
    public static <E, V> PageResult<V> of(PageResult<E> source, Function<E, V> mapper) {
        List<V> voList = source.getRecords() == null
                ? Collections.emptyList()
                : source.getRecords().stream().map(mapper).toList();
        return new PageResult<V>()
                .setPageNum(source.getPageNum())
                .setPageSize(source.getPageSize())
                .setTotal(source.getTotal())
                .setRecords(voList);
    }

}
