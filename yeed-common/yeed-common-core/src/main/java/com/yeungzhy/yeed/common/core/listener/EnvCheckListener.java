package com.yeungzhy.yeed.common.core.listener;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 本地开发环境检查监听器，在以下场景会终止 JVM（退出码 1）：
 * <ol>
 *   <li>激活的 profile 为 {@code test(pord)}，且未设置部署模式标识（即 {@code DEPLOY_MODE != "server"}）
 *       <p> 防止在本地开发时误连公共环境，即使缺少部分网络关系
 *   </li>
 *
 *   <li>激活的 profile 为 {@code dev}，且 Nacos 服务发现组名也为 {@code dev}
 *       <p> 强制要求开发环境使用非默认分组，避免服务注册冲突
 *   </li>
 * </ol>
 *
 * <h3>SPI 注册方式</h3>
 * <p>
 * 本监听器通过 {@code META-INF/spring.factories} 注册，key 为
 * {@code org.springframework.context.ApplicationListener}，value 为当前类的全限定名。
 * </p>
 *
 * <h4>为什么要这样做？</h4>
 * <p>
 * Spring Boot 在启动早期（{@link ApplicationEnvironmentPreparedEvent} 发布前）就需要加载并注册
 * {@link ApplicationListener}，以便在环境准备阶段就能执行检查逻辑。而
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 中的类是在自动配置处理阶段（较晚）才被加载的，
 * 其设计目标仅为 {@link org.springframework.boot.autoconfigure.AutoConfiguration} 类。
 * 因此，监听器若放在该文件中，会被完全忽略。
 * </p>
 * <p>
 * 虽然 Spring Boot 3.x 已废弃 {@code spring.factories} 中
 * {@code EnableAutoConfiguration} 的注册方式，但对于
 * {@code ApplicationListener}、{@code EnvironmentPostProcessor} 等早期扩展点，
 * {@code spring.factories} 仍然是官方唯一支持的 SPI 机制。
 * </p>
 *
 * @author yeungzhy
 * @date 2026-08-02 18:19
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