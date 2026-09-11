package com.yeungzhy.yeed.openapi.app.dto;

import com.yeungzhy.yeed.common.core.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * OpenApi 接入应用 分页查询 DTO（前端入参）
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class OpenapiAppPageDTO extends PageRequest {


}
