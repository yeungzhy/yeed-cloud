package com.yeungzhy.yeed.common.core.listener;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 本地开发环境检查监听器，在以下场景会终止 JVM（退出码 1）：
 * <ol>
 *   <li>激活的 profile 为 {@code test(pord)}，且未设置部署模式标识（即 {@code DEPLOY_MODE != "server"}）
 *       <p>防止在本地开发时误连公共环境，即使缺少部分网络关系</li>
 *   <li>激活的 profile 为 {@code dev}，且 Nacos 服务发现组名也为 {@code dev}
 *       <p>强制要求开发环境使用非默认分组，避免服务注册冲突</li>
 * </ol>
 *
 * <p><b>SPI 注册方式</b>：通过 {@code META-INF/spring.factories} 注册（key 为
 * {@code org.springframework.context.ApplicationListener}），而非 {@code AutoConfiguration.imports}。
 * 原因：{@link ApplicationListener} 需在启动早期（{@link ApplicationEnvironmentPreparedEvent} 发布前）
 * 完成注册才能生效，而 imports 文件到自动配置阶段（较晚）才被加载，且仅用于
 * {@link org.springframework.boot.autoconfigure.AutoConfiguration} 类；虽然 Spring Boot 3.x
 * 已废弃 spring.factories 中的 EnableAutoConfiguration 注册，但 ApplicationListener /
 * EnvironmentPostProcessor 等早期扩展点仍只能走该机制。
 *
 * @author yeungzhy
 * @since 2026-08-02
 */
public class EnvCheckListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** 部署模式标识 键 */
    private static final String DEPLOY_MODE_KEY = "DEPLOY_MODE";
    /** 部署模式标识 值 */
    private static final String DEPLOY_MODE_SERVER = "server";
    /** 测试环境 */
    private static final String PROFILE_TEST = "test";
    /** 开发环境 */
    private static final String PROFILE_DEV = "dev";


    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        // 获取当前激活环境
        ConfigurableEnvironment env = event.getEnvironment();
        String activeProfile = env.getProperty("spring.profiles.active");

        if (PROFILE_TEST.equals(activeProfile)) {

            // 获取环境中的 DEPLOY_MODE, 兼容两种配置方式: -DDEPLOY_MODE=server 和 application.yaml
            String deployMode = env.getProperty(DEPLOY_MODE_KEY);

            // 如果没有“服务器标识”，则认为是本地启动，强制拦截
            if (!DEPLOY_MODE_SERVER.equalsIgnoreCase(deployMode)) {
                String msg = """
                        
                        
                        ╔═══════════════════════════════════════╗
                        ║                                       ║
                        ║      禁止在开发环境运行测试环境       ║
                        ║                                       ║
                        ╚═══════════════════════════════════════╝
                        """;
                System.err.println(msg);
                System.exit(1);
            }
        }

        if (PROFILE_DEV.equals(activeProfile)) {
            String activeGroup = env.getProperty("spring.cloud.nacos.discovery.group");
            if (PROFILE_DEV.equals(activeGroup)) {
                String msg = """
                        
                        
                        ╔═══════════════════════════════════════════════════╗
                        ║                                                   ║
                        ║   禁止在开发环境使用默认分组名注册服务到 Nacos    ║
                        ║                                                   ║
                        ╚═══════════════════════════════════════════════════╝
                        """;
                System.err.println(msg);
                System.exit(1);
            }
        }
    }
}