package com.yeungzhy.yeed.openapi.app.service;

import com.yeungzhy.yeed.openapi.app.entity.OpenapiApp;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import com.yeungzhy.yeed.common.data.request.BaseSorts;
import org.springframework.stereotype.Component;

/**
 * OpenApi 接入应用 排序字段白名单
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Component
public class OpenapiAppSorts extends BaseSorts<OpenapiApp> {

    protected OpenapiAppSorts() {
        super(OpenapiApp.class, new BaseSorts.Builder<OpenapiApp>()
                .add(BaseEntity.Fields.createTime, OpenapiApp::getCreateTime)
                // 多个字段排序时,要加上主键作为决胜字段,防止在其他字段相同的发生数据错乱或漏页
                .add(BaseEntity.Fields.id, OpenapiApp::getId)
        );
    }

}
