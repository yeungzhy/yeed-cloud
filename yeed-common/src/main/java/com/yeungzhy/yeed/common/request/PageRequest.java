package com.yeungzhy.yeed.common.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Range;

@Data
@Accessors(chain = true)
public class PageRequest {

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

    /**
     * 构造 MyBatis-Plus 分页对象
     * <p> 后期需要扩展排序时, 再从当前对象中获取 orderField/isAsc 字段
     *
     * @return 分页对象，current/size 取自当前 pageNum/pageSize
     */
    public <T> Page<T> toPage() {
        return new Page<>(pageNum, pageSize);
    }

}
