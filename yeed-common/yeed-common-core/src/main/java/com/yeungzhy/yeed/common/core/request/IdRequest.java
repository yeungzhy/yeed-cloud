package com.yeungzhy.yeed.common.core.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按唯一标识（主键 ID）操作的基础请求体
 *
 * <p> 通常继承后附加业务参数，或直接用于「根据 ID 查询/更新/删除」类接口
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Data
@Accessors(chain = true)
public class IdRequest {

    /** 业务主键 ID */
    @NotNull(message = "ID不能为空")
    private Long id;

}
