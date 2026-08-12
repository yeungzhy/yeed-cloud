package com.yeungzhy.yeed.admin.sys.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserMapper;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserConvert;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserSorts;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.common.core.crypto.BlindIndexProvider;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;

/**
 * 系统用户 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {
    /* 几乎只有这里用到单向加密密码,就不@Bean注册到容器了 */
    private final Argon2PasswordEncoder argon2PwdEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Resource
    private SysUserSorts sysUserSorts;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysUserConvert sysUserConvert;
    @Resource
    private BlindIndexProvider emailBlindIndex;


    @Override
    public Long save(SysUserAddDTO dto) {
        String username = dto.getUsername();
        BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getUsername, username), "用户名已存在");

        String plaintextEmail = dto.getEmail();
        String emailBidx = emailBlindIndex.generateHex(plaintextEmail);
        BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getEmailBidx, emailBidx), "邮箱已存在");

        SysUser sysUser = SysUser.builder()
                .username(username)
                .password(argon2PwdEncoder.encode(dto.getPassword()))
                // email 明文入库，由 FieldCryptoInterceptor 在写库前自动 AES 加密
                .email(plaintextEmail)
                .emailBidx(emailBidx)
                .build();

        sysUserMapper.insert(sysUser);
        return sysUser.getId();
    }


    @Override
    public void update(SysUserUpdateDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        SysUser sysUser = sysUserMapper.selectById(dto.getId());
        BizAssert.notNull(sysUser, "用户不存在");
        BizAssert.isTrue(argon2PwdEncoder.matches(dto.getPassword(), sysUser.getPassword()), "密码不正确");

        SysUser updateEntity = SysUser.builder().id(dto.getId()).build();

        String username = dto.getUsername();
        if (StringUtils.isNotEmpty(username)) {
            BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getUsername, username), "用户名已存在");
            updateEntity.setUsername(username);
        }
        String email = dto.getEmail();
        if (StringUtils.isNotEmpty(email)) {
            // 加密的敏感字段, 需要用盲索引定位
            String emailBidx = emailBlindIndex.generateHex(email);
            BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getEmailBidx, emailBidx), "邮箱已存在");

            // email 明文入库，由 FieldCryptoInterceptor 在写库前自动 AES 加密
            updateEntity.setEmail(email);
            updateEntity.setEmailBidx(emailBidx);
        }
        sysUserMapper.updateById(updateEntity);
    }


    @Override
    public SysUserVO detail(Long id) {
        SysUser entity = sysUserMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return sysUserConvert.toVO(entity);
    }


    @Override
    public PageResult<SysUserVO> page(SysUserPageDTO dto) {
        LambdaQueryWrapper<SysUser> lambdaQuery = Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getEmail, SysUser::getStatus, SysUser::getCreateTime)
                .like(StringUtils.isNotEmpty(dto.getUsername()), SysUser::getUsername, dto.getUsername())
                .eq(Objects.nonNull(dto.getStatus()), SysUser::getStatus, dto.getStatus());
        /*
         * 邮箱查询条件需要用邮箱信息生成盲索引进行查询。
         * 注意：不能用 .eq(condition, column, generateHex(...))
         * generateHex 会在 eq 的 condition 判断之前执行，空邮箱会触发 BlindIndexProvider 的 null 校验
         * 该开关只短路 SQL 拼接，不短路实参求值；对带副作用/会抛异常的实参，必须用 if 守卫。
         */
        String email = dto.getEmail();
        if (StringUtils.isNotEmpty(email)) {
            lambdaQuery.eq(SysUser::getEmailBidx, emailBlindIndex.generateHex(email));
        }

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysUserSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return sysUserMapper.selectPageVO(dto, lambdaQuery, sysUserConvert::toVO);
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysUserMapper.deleteByIdAutoFill(id);
    }


    @Override
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        // 批量逻辑删除：空集合不触库，超量自动分片，删除人自动填充
        sysUserMapper.deleteByIdsAutoFill(ids);
    }


    @Override
    public PageResult<SysUserVO> pageWithDeleted(SysUserPageDTO dto) {
        // TODO 构建查询条件
        LambdaQueryWrapper<SysUser> lambdaQuery = Wrappers.<SysUser>lambdaQuery();

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysUserSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        // 回收站分页：查询包含已逻辑删除的数据（与 page 的区别是不携带条件 delete_time = 0）
        return sysUserMapper.selectPageVOWithDeleted(dto, lambdaQuery, sysUserConvert::toVO);
    }


    @Override
    public void restoreById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 恢复已逻辑删除的数据；对未删除数据执行返回 0（天然幂等）
        BizAssert.isTrue(sysUserMapper.restoreById(id) > 0, "记录不存在或未删除");
    }


    @Override
    public void physicalDeleteById(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        // 物理删除（真 DELETE）：不可恢复，仅用于"回收站彻底删除"等场景
        sysUserMapper.physicalDeleteById(id);
    }


}
