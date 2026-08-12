package com.yeungzhy.yeed.admin.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMapper;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUserRole;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserMapper;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserRoleMapper;
import com.yeungzhy.yeed.common.core.enums.BuiltinRoleEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 默认数据初始化器（系统首次上线引导）
 *
 * <p>在应用启动完成后，按需初始化默认角色与默认超管账号：
 * <ul>
 *     <li>角色编码等结构性数据定义在代码常量（{@link BuiltinRoleEnum}），随版本发布，不进配置中心；</li>
 *     <li>超管账号密码属环境敏感信息，从配置中心读取（{@link DefaultDataProperties}，{@code app.init.default-data}），
 *         不同环境各自维护；</li>
 *     <li>「先查后插」保证幂等；多实例并发首启的竞态由唯一索引兜底，
 *         捕获 {@link DuplicateKeyException} 视为其他实例已完成初始化；</li>
 *     <li>预期外异常不捕获，直接抛出终止启动（fail-fast），默认数据缺失时系统本就不应提供服务。</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
@Slf4j
@Component
public class DefaultDataInitializer implements ApplicationRunner {
    /* 与 SysUserServiceImpl 保持一致的 Argon2 编码器，不注册 @Bean（仅初始化场景使用） */
    private final Argon2PasswordEncoder argon2PwdEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Resource
    private DefaultDataProperties defaultDataProperties;

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!defaultDataProperties.isEnabled()) {
            log.info("默认数据初始化未启用（app.init.default-data.enabled=false），跳过");
            return;
        }

        DefaultDataProperties.SuperAdmin superAdmin = defaultDataProperties.getSuperAdmin();
        if (!StringUtils.hasText(superAdmin.getUsername()) || !StringUtils.hasText(superAdmin.getDefaultPassword())) {
            throw new IllegalStateException("默认数据初始化已启用，但 super-admin.username / default-password 未配置，请检查 Nacos 配置");
        }

        initSuperAdmin(superAdmin);
        initRoles();
        initUserRoles();
    }



    /**
     * 初始化默认超管账号
     *
     * <p>密码经 Argon2 单向哈希后落库，与业务新增用户一致、不依赖环境密钥；
     * 先查后插保证幂等，并发首启的插队竞态由唯一索引兜底（{@link DuplicateKeyException}）。
     */
    private void initSuperAdmin(DefaultDataProperties.SuperAdmin superAdmin) {
        String username = superAdmin.getUsername();

        boolean exists = sysUserMapper.existsByColumn(SysUser::getUsername, username);
        if (exists) {
            log.info("超管账号已存在，跳过: username={}", username);
            return;
        }

        SysUser sysUser = SysUser.builder()
                .username(username)
                // 超管固定工号
                .employeeNo("1")
                // Argon2 单向哈希，与业务新增用户完全一致；不依赖环境密钥
                .password(argon2PwdEncoder.encode(superAdmin.getDefaultPassword()))
                .status(1)
                .createBy(0L)
                .build();
        try {
            sysUserMapper.insert(sysUser);
            log.info("超管账号初始化完成: username={}, id={}", username, sysUser.getId());
        } catch (DuplicateKeyException e) {
            // 多实例并发首启时，另一实例已抢先插入，唯一索引冲突即代表数据已就绪
            log.info("超管账号已由其他实例插入，跳过: username={}", username);
        }
    }


    /**
     * 初始化默认角色
     *
     * <p>数据源为 {@link BuiltinRoleEnum} 全量枚举，保证各环境角色编码与代码版本强一致；
     * 幂等与并发兜底策略同 {@link #initSuperAdmin(DefaultDataProperties.SuperAdmin)}。
     */
    private void initRoles() {
        for (BuiltinRoleEnum roleEnum : BuiltinRoleEnum.values()) {
            boolean exists = sysRoleMapper.existsByColumn(SysRole::getRoleCode, roleEnum.getRoleCode());
            if (exists) {
                log.info("默认角色已存在，跳过: code={}", roleEnum.getRoleCode());
                continue;
            }

            SysRole sysRole = SysRole.builder()
                    .roleCode(roleEnum.getRoleCode())
                    .roleName(roleEnum.getRoleName())
                    .description(roleEnum.getDescription())
                    .status(1)
                    // 系统引导数据，无登录上下文，固定 0 表示系统创建（避免自动填充随机值）
                    .createBy(0L)
                    .build();
            try {
                sysRoleMapper.insert(sysRole);
                log.info("默认角色初始化完成: code={}, id={}", roleEnum.getRoleCode(), sysRole.getId());
            } catch (DuplicateKeyException e) {
                // 多实例并发首启时，另一实例已抢先插入，唯一索引冲突即代表数据已就绪
                log.info("默认角色已由其他实例插入，跳过: code={}", roleEnum.getRoleCode());
            }
        }
    }


    /**
     * 初始化超管用户-角色关联（超管 → {@link BuiltinRoleEnum#SUPER_ADMIN}）
     *
     * <p>按用户名/角色编码反查主键后落关联；任一侧缺失直接 fail-fast，
     * 否则超管登录后将无角色可用（等同系统不可用）。
     */
    private void initUserRoles() {
        String username = defaultDataProperties.getSuperAdmin().getUsername();
        SysUser admin = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        SysRole superAdminRole = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, BuiltinRoleEnum.SUPER_ADMIN.getRoleCode()));

        // 防御性 fail-fast：关联缺失将导致超管登录后无角色可用，系统等同不可用
        if (admin == null || superAdminRole == null) {
            throw new IllegalStateException("默认数据初始化异常：超管用户或 SUPER_ADMIN 角色不存在，请检查初始化日志");
        }

        Long userId = admin.getId();
        Long roleId = superAdminRole.getId();
        // 幂等：按 (userId, roleId) 判断关联是否已存在
        boolean exists = sysUserRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId)) > 0;
        if (exists) {
            log.info("超管用户-角色关联已存在，跳过: userId={}, roleId={}", userId, roleId);
            return;
        }

        SysUserRole sysUserRole = SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .build();
        try {
            sysUserRoleMapper.insert(sysUserRole);
            log.info("超管用户-角色关联初始化完成: userId={}, roleId={}", userId, roleId);
        } catch (DuplicateKeyException e) {
            // 多实例并发首启时，另一实例已抢先插入，唯一索引冲突即代表数据已就绪
            log.info("超管用户-角色关联已由其他实例插入，跳过: userId={}, roleId={}", userId, roleId);
        }
    }



}
