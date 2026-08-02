package com.yeungzhy.yeed.common.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class IdRequest {

    /** 唯一标识, 如 ID/Code */
    @NotNull(message = "ID不能为空")
    private Long id;

    // 如果未来有租户隔离，可以加 tenantId，不影响旧接口
    // private String tenantId;

}
