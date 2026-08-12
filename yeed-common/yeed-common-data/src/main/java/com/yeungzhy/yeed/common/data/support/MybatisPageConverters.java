package com.yeungzhy.yeed.common.data.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import com.yeungzhy.yeed.common.core.result.PageResult;

import java.util.Collections;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页对象与 common-core 的 PageRequest/PageResult 之间的转换工具
 *
 * <p><b>职责</b>：将 "与 MyBatis-Plus 的互相转换" 集中在 common-data 层，
 * 使 common-core 的 PageRequest/PageResult 不耦合任何 ORM 框架。
 *
 * <p><b>用法</b>：业务侧一般不直接调用本工具，统一走
 * {@link com.yeungzhy.yeed.common.data.mybatis.BaseMapper#selectPageVO(PageRequest, com.baomidou.mybatisplus.core.conditions.Wrapper, Function)}
 * 一行完成分页查询，本工具是该方法内部的底层转换器：
 * <pre>
 * return sysUserMapper.selectPageVO(dto, lambdaQuery, sysUserConvert::toVo);
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-11
 */
public final class MybatisPageConverters {

    private MybatisPageConverters() {}

    /**
     * 从 common-core 的 PageRequest 构造 MyBatis-Plus 的 Page 对象
     *
     * @param pageRequest 分页请求（pageNum / pageSize）
     * @param <T>         实体类型
     * @return MyBatis-Plus 分页对象，current/size 取自 pageRequest
     */
    public static <T> Page<T> toMybatisPlusPage(PageRequest pageRequest) {
        return new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 从 MyBatis-Plus 的 IPage 直接转成 PageResult
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  实体类型
     * @return common-core 的 PageResult
     */
    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        return PageResult.of(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords()
        );
    }

    /**
     * 把 IPage&lt;ENTITY&gt; 转成 PageResult&lt;VO&gt;，常用：entity 列表转成 vo 列表
     *
     * @param page   MyBatis-Plus 分页结果
     * @param mapper entity → vo 转换函数
     * @param <E>    实体类型
     * @param <V>    VO 类型
     * @return common-core 的 PageResult，records 已通过 mapper 转换
     */
    public static <E, V> PageResult<V> toPageResult(IPage<E> page, Function<E, V> mapper) {
        return PageResult.of(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords() == null
                        ? Collections.emptyList()
                        : page.getRecords().stream().map(mapper).toList()
        );
    }
}
