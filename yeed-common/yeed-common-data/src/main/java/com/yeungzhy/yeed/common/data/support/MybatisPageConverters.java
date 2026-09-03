package com.yeungzhy.yeed.common.data.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeungzhy.yeed.common.core.request.PageRequest;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.data.mybatis.BaseMapper;

import java.util.Collections;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页对象与 common-core 的 PageRequest / PageResult 之间的转换器
 *
 * <p>存在的意义是让 common-core 的 {@link PageRequest} / {@link PageResult} 不耦合任何 ORM 类型：
 * 转换只在 common-data 这一层发生，core 侧保持纯 POJO。
 *
 * <p>业务侧不直接调用本工具，统一走 {@link BaseMapper#selectPageVO}——本工具是它内部
 * “PageRequest → Page”“Page → PageResult”两步转换的落点：
 * <pre>{@code return sysUserMapper.selectPageVO(dto, lambdaQuery, sysUserConvert::toVo);}</pre>
 *
 * @author yeungzhy
 * @since 2026-08-11
 * @see BaseMapper#selectPageVO
 */
public final class MybatisPageConverters {

    private MybatisPageConverters() {}

    /**
     * 用 PageRequest 的页码与条数构造 MyBatis-Plus 的 Page 对象
     *
     * @param pageRequest 分页请求，不能为 null；页码与条数的取值边界见 {@link PageRequest} 上的校验注解
     * @param <T>         记录类型
     * @return MyBatis-Plus 分页对象，current / size 取自 pageRequest
     */
    public static <T> Page<T> toMybatisPlusPage(PageRequest pageRequest) {
        return new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 把 MyBatis-Plus 分页结果转成 PageResult，记录原样透传
     *
     * @param page 分页结果，不能为 null；records 为 null 时由 {@link PageResult#of} 兜底成空列表
     * @param <T>  记录类型
     * @return common-core 的 PageResult，records 与入参是同一个列表实例
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
     * 把 MyBatis-Plus 分页结果转成 PageResult，并逐条把实体转换成 VO
     *
     * <p>records 为 null 时转为空列表，不把 null 传给下游：分页结果常被继续流式处理，
     * 一处 null 会沿着调用链散成多处判空
     *
     * @param page   分页结果，不能为 null
     * @param mapper 实体到 VO 的转换函数，不能为 null；通常传生成器产出的 {@code XxxConvert::toVo}
     * @param <E>    实体类型
     * @param <V>    VO 类型
     * @return common-core 的 PageResult，records 已逐条转换
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
