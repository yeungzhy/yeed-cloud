package com.yeungzhy.yeed.openapi.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.common.core.crypto.RsaUtil;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.openapi.app.cache.OpenapiAppPubKeyCache;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppDTO;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppPageDTO;
import com.yeungzhy.yeed.openapi.app.dto.OpenapiAppSaveDTO;
import com.yeungzhy.yeed.openapi.app.entity.OpenapiApp;
import com.yeungzhy.yeed.openapi.app.mapper.OpenapiAppMapper;
import com.yeungzhy.yeed.openapi.app.service.OpenapiAppConvert;
import com.yeungzhy.yeed.openapi.app.service.OpenapiAppService;
import com.yeungzhy.yeed.openapi.app.service.OpenapiAppSorts;
import com.yeungzhy.yeed.openapi.app.vo.OpenapiAppVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * OpenApi 接入应用 服务实现类
 *
 * @author yeungzhy
 * @since 2026-09-09 20:27:24
 */
@Slf4j
@Service
public class OpenapiAppServiceImpl implements OpenapiAppService {

    @Resource
    private OpenapiAppSorts openapiAppSorts;
    @Resource
    private OpenapiAppMapper openapiAppMapper;
    @Resource
    private OpenapiAppConvert openapiAppConvert;
    @Resource
    private OpenapiAppPubKeyCache openapiAppPubKeyCache;


    @Override
    public OpenapiAppVO save(OpenapiAppSaveDTO dto) {
        // DTO -> Entity：同名字段由 MapStruct 自动映射
        OpenapiApp entity = openapiAppConvert.toEntity(dto);
        Boolean appIdExists = openapiAppMapper.existsByColumn(OpenapiApp::getAppId, entity.getAppId());
        BizAssert.isFalse(appIdExists, "应用 ID 已存在");

        String[] keyPair = RsaUtil.generateKeyPair();
        entity.setPublicKey(keyPair[0]);
        openapiAppMapper.insert(entity);
        // 网关验签只读公钥缓存，新增后立即回写
        openapiAppPubKeyCache.refresh(entity.getId());

        // 回显应用签名私钥
        return new OpenapiAppVO()
                .setId(entity.getId())
                .setAppId(entity.getAppId())
                .setAppName(entity.getAppName())
                .setRemark(keyPair[1]);
    }


    @Override
    public void update(OpenapiAppDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        // DTO -> Entity：id 与业务字段均自动映射
        OpenapiApp entity = openapiAppConvert.toEntity(dto);
        openapiAppMapper.updateById(entity);
        // 公钥轮换或停用都在此生效，停用即清键
        openapiAppPubKeyCache.refresh(dto.getId());
    }


    @Override
    public OpenapiAppVO detail(Long id) {
        OpenapiApp entity = openapiAppMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return openapiAppConvert.toVO(entity);
    }


    @Override
    public PageResult<OpenapiAppVO> page(OpenapiAppPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<OpenapiApp> lambdaQuery = Wrappers.<OpenapiApp>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        openapiAppSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return openapiAppMapper.selectPageResult(dto, lambdaQuery, openapiAppConvert::toVO);
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 逻辑删除后回查不到 appId，须先取出再删，随后清掉网关验签用的公钥缓存
        OpenapiApp entity = openapiAppMapper.selectById(id);
        openapiAppMapper.deleteByIdAutoFill(id);
        if (Objects.nonNull(entity)) {
            openapiAppPubKeyCache.evict(entity.getAppId());
        }
    }

}
