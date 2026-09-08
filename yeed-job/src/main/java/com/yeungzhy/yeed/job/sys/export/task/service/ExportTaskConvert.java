package com.yeungzhy.yeed.job.sys.export.task.service;

import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.api.export.task.dto.ExportTaskSaveDTO;
import com.yeungzhy.yeed.api.export.task.vo.ExportTaskPageVO;
import com.yeungzhy.yeed.job.sys.export.task.entity.ExportTask;
import org.mapstruct.Mapper;

/**
 * 导出任务表 对象转换器（Entity / DTO / VO 互转）
 *
 * <p> 实现类由 MapStruct 注解处理器在编译期生成，有问题时先执行一次编译，不要手工补实现类
 *
 * <p> 映射按字段名自动匹配，由此产生一条必须记住的约定：两端字段名不一致不会编译报错，只会静默映射为 null。
 * 模型字段改名、或字段名与源模型不同时，必须用 {@link org.mapstruct.Mapping} 显式指定
 *
 * <p> 枚举转整数无内建映射：需在本接口补 {@code default} 钩子方法，MapStruct 自动命中
 *
 *  @author yeungzhy
 * @since 2026-09-01 21:55:57
 */
@Mapper(componentModel = "spring")
public interface ExportTaskConvert {

    /** Entity → VO（详情 / 分页列表出参） */
    ExportTaskPageVO toVO(ExportTask entity);

    /** DTO → Entity（save / update 入参） */
    ExportTask toEntity(ExportTaskSaveDTO dto);


    /** 导出类型枚举 → 字符串 key */
    default String exportTypeToKey(ExportTypeEnum type) {
        return type == null ? null : type.getKey();
    }


}
