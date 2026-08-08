package com.yeungzhy.yeed.admin.bootstrap;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 默认数据初始化配置项
 *
 * <p>配置示例（Nacos 按环境隔离，生产首次上线置 enabled=true，初始化完成后置 false）：
 *
 * {@snippet lang="yaml":
 * app:
 *   init:
 *     default-data:
 *       enabled: true
 *       super-admin:
 *         username: admin
 *         default-password: ${ADMIN_INIT_PASSWORD}
 * }
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
@Data
@Accessors(chain = true)
@ConfigurationProperties(prefix = "app.init.default-data")
public class DefaultDataProperties {

    /** 是否启用默认数据初始化（默认关闭，生产首次上线开启） */
    private boolean enabled = false;

    /** 默认超管账号配置 */
    private SuperAdmin superAdmin = new SuperAdmin();


    /** 默认超管账号 */
    @Data
    @Accessors(chain = true)
    public static class SuperAdmin {

        /** 超管用户名 */
        private String username;

        /** 初始密码（明文，初始化时使用 Argon2 单向哈希后入库，首次登录后应立即修改） */
        private String defaultPassword;

    }

}
