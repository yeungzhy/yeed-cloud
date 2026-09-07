package com.yeungzhy.yeed.job.export.engine;

import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.job.export.engine.api.Exporter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出器注册表（按导出类型路由）
 *
 * <p>构造器注入自动收集容器内全部 {@link Exporter} 实现，再按各实现自报的
 * {@link Exporter#getExportType()} 重建路由表——不用 bean 名作键，
 * 避免 bean 名与 {@link ExportTypeEnum} 的 key 各自漂移后对不上。
 *
 * <p>启动期 fail-fast，两类错误都在构造时暴露，不会拖到用户点了导出按钮才发现：
 * <ul>
 *   <li>重复：同一导出类型有多个实现；</li>
 *   <li>缺失：{@link ExportTypeEnum#values()} 中任一类型没有实现——「枚举加了类型、没写导出器」。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-22
 */
@Component
public class ExporterRegistry {

    /** 导出类型 key → 导出器（构造时一次性构建，之后只读） */
    private final Map<String, Exporter<?, ?>> exporterByType;

    /**
     * 收集全部导出器并按导出类型重建路由表
     *
     * @param exporters Spring 自动收集的全部 {@link Exporter} 实现，允许为空集合
     * @throws IllegalStateException 同一导出类型存在多个实现，或 {@link ExportTypeEnum} 中存在类型没有实现
     */
    public ExporterRegistry(List<Exporter<?, ?>> exporters) {
        Map<String, Exporter<?, ?>> byType = new HashMap<>();
        for (Exporter<?, ?> exporter : exporters) {
            String typeKey = exporter.getExportType().getKey();
            Exporter<?, ?> existed = byType.putIfAbsent(typeKey, exporter);
            if (existed != null) {
                throw new IllegalStateException("启动异常：导出类型 [%s] 存在多个 Exporter 实现：%s 与 %s"
                        .formatted(typeKey, existed.getClass().getName(), exporter.getClass().getName()));
            }
        }
        for (ExportTypeEnum type : ExportTypeEnum.values()) {
            if (!byType.containsKey(type.getKey())) {
                throw new IllegalStateException("启动异常：导出类型 [%s] 未找到对应 Exporter 实现".formatted(type.getKey()));
            }
        }
        this.exporterByType = Collections.unmodifiableMap(byType);
    }

    /**
     * 按导出类型 key 取导出器（封闭域 fail-fast）
     *
     * <p>key 与任务实体的导出类型列同源；启动期已做过全覆盖校验，运行期抛出即意味着 key 未走枚举、是脏数据。
     *
     * @param typeKey 导出类型 key，不能为 null
     * @return 该类型对应的导出器
     * @throws IllegalArgumentException key 没有对应实现时抛出
     */
    public Exporter<?, ?> getExporter(String typeKey) {
        Exporter<?, ?> exporter = exporterByType.get(typeKey);
        if (exporter == null) {
            throw new IllegalArgumentException("未注册的导出类型: " + typeKey);
        }
        return exporter;
    }

}
