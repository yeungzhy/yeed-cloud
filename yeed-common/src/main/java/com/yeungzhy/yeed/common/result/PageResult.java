package com.yeungzhy.yeed.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /** 从 MyBatis-Plus 的 Page 直接转换 */
    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<T>()
                .setPageNum(page.getCurrent())
                .setPageSize(page.getSize())
                .setTotal(page.getTotal())
                .setRecords(page.getRecords());
    }

    /** 把 Page<ENTITY> 转成 PageResult<VO>，常用：entity 列表转成 vo 列表 */
    public static <E, V> PageResult<V> of(Page<E> page, Function<E, V> mapper) {
        List<V> voList = page.getRecords() == null
                ? Collections.emptyList()
                : page.getRecords().stream().map(mapper).toList();
        return new PageResult<V>()
                .setPageNum(page.getCurrent())
                .setPageSize(page.getSize())
                .setTotal(page.getTotal())
                .setRecords(voList);
    }

}
