package com.yeungzhy.yeed.common.core.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按主键 ID 集合批量操作的基础请求体
 *
 * <p>用于"根据 ID 批量删除 / 批量恢复 / 批量物理删除"类接口，
 * 避免直接以裸 {@code List<Long>} 作为入参（无法承载校验注解，也不利于后续扩展业务字段）。
 */
@Data
@Accessors(chain = true)
public class IdsRequest {

    /** 业务主键 ID 集合 */
    @NotEmpty(message = "ID列表不能为空")
    private List<Long> ids;

}
