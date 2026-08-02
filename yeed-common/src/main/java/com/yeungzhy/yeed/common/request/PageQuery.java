package com.yeungzhy.yeed.common.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Range;

@Data
@Accessors(chain = true)
public class PageQuery {

    /** 页码 */
    @NotNull(message = "页码不能为空")
    @Range(min = 1, max = 3000, message = "页码必须在 1-3000 之间")
    private Integer pageNum = 1;

    /** 每页条数 */
    @NotNull(message = "每页条数不能为空")
    @Range(min = 1, max = 2000, message = "每页条数必须在 1-2000 之间")
    private Integer pageSize = 10;

    // 可选：排序字段，字段需要在后端维护, 防止SQL注入
    // private String orderField;
    // private Boolean isAsc;

}
