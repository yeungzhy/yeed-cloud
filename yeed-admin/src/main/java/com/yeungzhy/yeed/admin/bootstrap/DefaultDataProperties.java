package com.yeungzhy.yeed.admin.bootstrap;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * 默认数据初始化配置项
 *
 * <p>配置示例（Nacos 按环境隔离，生产首次上线置 enabled=true，初始化完成后置 false）：
 * {@snippet lang="yaml":
 * yeed-admin:
 *   init:
 *     default-data:
 *       enabled: true
 *       super-admin:
 *         username: admin
 *         employee-no: 1
 *         default-password: ${ADMIN_INIT_PASSWORD}
 * }
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
@Data
@Validated
@Accessors(chain = true)
@ConfigurationProperties(prefix = "yeed-admin.init.default-data")
public class DefaultDataProperties {

    /** 是否启用默认数据初始化（默认关闭，生产首次上线开启） */
    private boolean enabled = false;

    /** 默认超管账号配置 */
    private SuperAdmin superAdmin = new SuperAdmin();

    /**
     * 启用初始化时，超管账号三项配置必须齐备（条件必填）
     *
     * <p>这三项只在 {@code enabled=true} 时参与初始化，配置错误绑定期就会暴露
     */
    @AssertTrue(message = "启用默认数据初始化时，super-admin.username / employee-no / default-password 均不能为空")
    public boolean isSuperAdminConfigured() {
        return !enabled || (StringUtils.hasText(superAdmin.username)
                && StringUtils.hasText(superAdmin.employeeNo)
                && StringUtils.hasText(superAdmin.defaultPassword));
    }

    /** 默认超管账号 */
    @Data
    @Accessors(chain = true)
    public static class SuperAdmin {

        /** 超管用户名 */
        private String username;

        /** 超管工号 */
        private String employeeNo;

        /** 初始密码（明文，初始化时使用 Argon2 单向哈希后入库，首次登录后应立即修改） */
        private String defaultPassword;

    }

}
