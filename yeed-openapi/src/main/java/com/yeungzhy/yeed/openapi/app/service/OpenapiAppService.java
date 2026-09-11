package com.yeungzhy.yeed.openapi.app.service;

import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppDTO;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppPageDTO;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppSaveDTO;
import com.yeungzhy.yeed.openapi.app.vo.OpenapiAppVO;

/**
 * OpenApi 接入应用 服务类
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
public interface OpenapiAppService {

    // ==================== 标准写入（CUD） ====================

    /**
     * 新增
     *
     * @param dto 入参
     * @return 新增记录的主键 ID
     * @author yeungzhy
     * @since 2026-09-09 20:27:24
     */
    OpenapiAppVO save(OpenapiAppSaveDTO dto);

    /**
     * 更新
     *
     * @param dto 入参
     * @author yeungzhy
     * @since 2026-09-09 20:27:24
     */
    void update(OpenapiAppDTO dto);


    // ==================== 标准查询（R） ====================

    /**
     * 详情
     *
     * @param id 主键 ID
     * @return 详情数据
     * @author yeungzhy
     * @since 2026-09-09 20:27:24
     */
    OpenapiAppVO detail(Long id);

    /**
     * 分页查询
     *
     * @param dto 分页查询入参
     * @return 分页结果
     * @author yeungzhy
     * @since 2026-09-09 20:27:24
     */
    PageResult<OpenapiAppVO> page(OpenapiAppPageDTO dto);


    // ==================== 标准删除（逻辑删除） ====================

    /**
     * 删除（逻辑删除）
     *
     * @param id 主键 ID
     * @author yeungzhy
     * @since 2026-09-09 20:27:24
     */
    void delete(Long id);


}
