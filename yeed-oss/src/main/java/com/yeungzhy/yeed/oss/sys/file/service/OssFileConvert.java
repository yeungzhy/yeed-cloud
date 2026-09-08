package com.yeungzhy.yeed.oss.sys.file.service;

import com.yeungzhy.yeed.oss.sys.file.dto.OssFileDTO;
import com.yeungzhy.yeed.oss.sys.file.entity.OssFile;
import com.yeungzhy.yeed.oss.sys.file.vo.OssFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 系统文件记录表 对象转换器（Entity / DTO / VO 互转）
 *
 * <p>实现类由 MapStruct 注解处理器在编译期生成，有问题时先执行一次编译，不要手工补实现类
 *
 * <p>映射按字段名自动匹配，由此产生一条必须记住的约定：两端字段名不一致不会编译报错，只会静默映射为 null；
 * 模型字段改名、或字段名与源模型不同时，必须用 {@link org.mapstruct.Mapping} 显式指定
 *
 * <p>枚举转整数无内建映射：需在本接口补 {@code default} 钩子方法，MapStruct 自动命中
 *
 * @author yeungzhy
 * @since 2026-09-04 12:00:19
 */
@Mapper(componentModel = "spring")
public interface OssFileConvert {

    /**
     * Entity → VO（详情 / 分页列表出参）
     *
     * <p>fileName 必须显式指定：VO 存的是"主名 + 判真扩展名"的完整展示名，实体 fileName 只存主名，
     * 同名不同义，靠字段自动匹配会静默映射成不带扩展名的主名，不报错也不易发现
     */
    @Mapping(target = "fileName", expression = "java(entity.displayName())")
    OssFileVO toVO(OssFile entity);

    /**
     * DTO → Entity（save / update 入参）
     *
     * <p>fileExt 必须显式忽略：扩展名只由上传时的字节判真写入，绝不接受外部入参，
     * 否则"不信任用户扩展名"这条收口形同虚设（改个名就能让下载产物挂上任意扩展名）
     */
    @Mapping(target = "fileExt", ignore = true)
    OssFile toEntity(OssFileDTO dto);

}
