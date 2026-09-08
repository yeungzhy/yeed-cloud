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
 * <p>初始密码属环境敏感信息，走配置中心按环境各自维护；生产首次上线置 {@code enabled=true}，
 * 初始化完成后改回 false（不落"已初始化"标记，幂等靠先查后插与唯一索引）
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
     * <p>这三项只在 {@code enabled=true} 时参与初始化，配置缺失在绑定期即暴露
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
