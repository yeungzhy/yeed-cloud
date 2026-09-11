package com.yeungzhy.yeed.openapi.app.service;

import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppDTO;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppSaveDTO;
import com.yeungzhy.yeed.openapi.app.entity.OpenapiApp;
import com.yeungzhy.yeed.openapi.app.vo.OpenapiAppVO;
import org.mapstruct.Mapper;

/**
 * OpenApi 接入应用 对象转换器（Entity / DTO / VO 互转）
 *
 * <p> 实现类由 MapStruct 注解处理器在编译期生成，排查问题先执行一次编译，不要手工补实现类
 *
 * <p> 映射按字段名自动匹配，两端字段名不一致不会编译报错，只会静默映射为 null
 * 因此模型字段改名、或字段名与源模型不同时，必须用 {@code org.mapstruct.Mapping} 显式指定
 *
 * <p> 枚举与整数互转无内建映射，需在本接口补 {@code default} 钩子方法，MapStruct 自动命中
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Mapper(componentModel = "spring")
public interface OpenapiAppConvert {

    /** Entity → VO */
    OpenapiAppVO toVO(OpenapiApp entity);

    /** DTO → Entity */
    OpenapiApp toEntity(OpenapiAppDTO dto);
    OpenapiApp toEntity(OpenapiAppSaveDTO dto);


    /** 启用型枚举 → 整数 code（MapStruct 转换钩子，枚举不能自动映射到 Integer） */
    default Integer enableStatToCode(EnableStatusEnum type) {
        return type == null ? null : type.getCode();
    }


}
