package com.yeungzhy.yeed.job.export.biz.user;

import com.yeungzhy.yeed.api.export.ExportTypeEnum;
import com.yeungzhy.yeed.api.export.user.UserExportFeignClient;
import com.yeungzhy.yeed.api.export.user.dto.UserExportPageDTO;
import com.yeungzhy.yeed.job.export.engine.api.ExportContext;
import com.yeungzhy.yeed.job.export.engine.api.Exporter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户列表导出器
 *
 * <p> 数据经 {@link UserExportFeignClient} 从 admin 侧拉取：job 不直连业务库，
 * 业务查询逻辑留在 admin，本导出器只做「取数 + 描述表头」
 *
 * @author yeungzhy
 * @since 2026-08-23
 * @see Exporter
 */
@Component
public class UserExporter implements Exporter<UserExportPageDTO, UserExportRow> {

    @Resource
    private UserExportFeignClient userExportFeignClient;

    @Override
    public ExportTypeEnum getExportType() {
        return ExportTypeEnum.USER_EXPORT;
    }

    @Override
    public QueryMode getQueryMode() {
        return QueryMode.PAGE;
    }

    @Override
    public Class<UserExportPageDTO> getParamType() {
        return UserExportPageDTO.class;
    }

    @Override
    public Long totalCount(ExportContext<UserExportPageDTO> ctx) {
        /*
         * RPC-Style：契约裸返回总行数，远程失败抛异常中断
         * 分页字段不参与命中判定（见 UserExportFeignClient#exportTotal）
         * 故即使 param 后续被翻页就地改写，也不影响这里取到的分母
         */
        return userExportFeignClient.exportTotal(ctx.param());
    }

    @Override
    public List<UserExportRow> pageQuery(ExportContext<UserExportPageDTO> ctx, int pageNum, int pageSize) {
        UserExportPageDTO query = ctx.param();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);

        // RPC-Style：契约裸返回本页数据
        return userExportFeignClient.exportPage(query).stream()
                .map(UserExportRow::from)
                .toList();
    }

    @Override
    public String getSheetName(ExportContext<UserExportPageDTO> ctx) {
        return "用户导出";
    }

    @Override
    public Class<UserExportRow> getFixedHead(ExportContext<UserExportPageDTO> ctx) {
        return UserExportRow.class;
    }

}
