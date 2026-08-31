package com.yeungzhy.yeed.api.feign;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Feign 兜底实现注册器：{@link EnableFeignFallbacks} 的核心扫描与注册逻辑。
 *
 * <p>由 {@link EnableFeignFallbacks} 经 {@link org.springframework.context.annotation.Import Import} 引入，
 * 实现 {@link ImportBeanDefinitionRegistrar} 与 {@link EnvironmentAware}：
 * <ul>
 *   <li>执行时机：容器 refresh 阶段，
 *       {@link org.springframework.context.annotation.ConfigurationClassPostProcessor} 处理配置类时
 *       即被调用（先于普通 Bean 实例化）。此时可向容器动态注册 BeanDefinition，
 *       适合"扫描发现 + 批量注册"，比 {@code @Bean} 方法更灵活</li>
 *   <li>为何实现 {@link EnvironmentAware}：{@link ClassPathScanningCandidateComponentProvider}
 *       构造需要 {@link Environment} 以解析候选类元数据（如注解属性中的占位符）</li>
 * </ul>
 *
 * <p>执行流程（{@link #registerBeanDefinitions}）：
 * <ol>
 *   <li>读取 {@link EnableFeignFallbacks} 的 basePackages / value 确定扫描范围（见 {@link #getBasePackages}）</li>
 *   <li>用 {@link ClassPathScanningCandidateComponentProvider} 扫描标注 {@link FeignFallback}
 *       的具体独立类（关闭默认过滤器，避免误扫 {@code @Component}）</li>
 *   <li>逐个解析并强校验兜底目标 Feign 接口（见 {@link #resolveFeignClientInterface}）</li>
 *   <li>注册为普通 Bean，beanName 与 {@code @Component} 命名规则一致（类名首字母小写），
 *       供 {@code @FeignClient(fallback / fallbackFactory = ...)} 按类引用</li>
 * </ol>
 *
 * <p>设计借鉴 Spring Cloud OpenFeign 的
 * {@link org.springframework.cloud.openfeign.FeignClientsRegistrar}（自定义注解 +
 * ImportBeanDefinitionRegistrar + 类路径扫描），实现思路一致。
 *
 * @author yeungzhy
 * @since 2026-08-10
 * @see EnableFeignFallbacks
 * @see FeignFallback
 */
public class FeignFallbacksRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    /** beanName 生成器：与 {@code @Component} 默认命名规则一致（类名首字母小写） */
    private static final AnnotationBeanNameGenerator BEAN_NAME_GENERATOR = new AnnotationBeanNameGenerator();
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 扫描并注册所有 {@link FeignFallback} 兜底类。
     *
     * <p>由 Spring 在配置类解析阶段调用：本方法不返回 Bean，而是通过
     * {@link BeanDefinitionRegistry} 将扫描到的兜底类注册为 BeanDefinition。
     *
     * @param importingClassMetadata 标注了 {@link EnableFeignFallbacks} 的配置类元数据
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 1. 读取 @EnableFeignFallbacks 注解属性
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(EnableFeignFallbacks.class.getName()));
        if (attributes == null) {
            // 标注类上没有 @EnableFeignFallbacks（理论上不会走到这），直接返回
            return;
        }

        // 2. 解析扫描包（逻辑与 @EnableFeignClients 一致）
        String[] basePackages = getBasePackages(importingClassMetadata, attributes);

        /*
         * 构建类路径扫描器并关闭默认过滤器（@Component/@Service 等）：只保留 @FeignFallback 过滤器，
         * 避免误扫其他组件。
         */
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                // 只接受具体类（非接口、非抽象类）且独立类（非内部类/非嵌套类）
                return beanDefinition.getMetadata().isConcrete()
                        && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(FeignFallback.class));

        // 4. 逐个包扫描并注册
        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                registerFallbackBean(candidate, registry);
            }
        }
    }

    /**
     * 解析扫描包：basePackages 优先，其次 value 简写，都未指定则取标注类所在包；
     * 解析顺序与 {@link org.springframework.cloud.openfeign.FeignClientsRegistrar} 保持一致。
     */
    private String[] getBasePackages(AnnotationMetadata importingClassMetadata, AnnotationAttributes attributes) {
        String[] basePackages = attributes.getStringArray("basePackages");
        if (basePackages.length == 0) {
            basePackages = attributes.getStringArray("value");
        }
        if (basePackages.length == 0) {
            basePackages = new String[]{ClassUtils.getPackageName(importingClassMetadata.getClassName())};
        }
        return basePackages;
    }

    /**
     * 注册单个 fallback / fallbackFactory 类为 Bean。
     *
     * <p>注册前先解析兜底目标 Feign 接口（见 {@link #resolveFeignClientInterface}），
     * 再做契约强校验（启动期 fail-fast，不可关闭）：普通 Fallback 必须实现目标接口、
     * FallbackFactory 泛型参数 T 必须等于目标接口。校验通过后以普通 Bean 形式注册，
     * beanName 与 {@code @Component} 默认命名规则一致（类名首字母小写）。
     */
    private void registerFallbackBean(BeanDefinition candidate, BeanDefinitionRegistry registry) {
        String beanClassName = candidate.getBeanClassName();
        Assert.hasText(beanClassName, "@FeignFallback 候选类缺少 beanClassName，扫描结果异常");

        Class<?> fallbackClass;
        try {
            fallbackClass = ClassUtils.forName(beanClassName, ClassUtils.getDefaultClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(String.format("扫描 @FeignFallback 时无法加载标注的类: %s", beanClassName), e);
        }

        FeignFallback annotation = AnnotatedElementUtils.findMergedAnnotation(fallbackClass, FeignFallback.class);
        Assert.notNull(annotation, "@FeignFallback 标注缺失，扫描结果异常: " + beanClassName);

        // 区分 FallbackFactory vs 普通 Fallback
        boolean isFallbackFactory = FallbackFactory.class.isAssignableFrom(fallbackClass);

        // 解析兜底目标 Feign 客户端接口：显式优先，未指定则自动推断
        Class<?> feignClientInterface = resolveFeignClientInterface(fallbackClass, annotation, isFallbackFactory);

        // 强校验（启动期 fail-fast，不可关闭）
        if (isFallbackFactory) {
            /*
             * 补一道契约校验：GenericTypeResolver 已在解析阶段校验 T 存在且标注 @FeignClient，
             * 这里确认 fallbackClass 确实是 FallbackFactory<T> 的实现，防止兜底目标与泛型参数不一致。
             */
            Class<?> factoryT = GenericTypeResolver.resolveTypeArgument(fallbackClass, FallbackFactory.class);
            if (factoryT == null || !factoryT.equals(feignClientInterface)) {
                throw new IllegalStateException(String.format(
                        "@FeignFallback FallbackFactory 校验失败：类 %s 泛型参数 %s 与兜底目标 %s 不一致",
                        fallbackClass.getName(), factoryT, feignClientInterface.getName()));
            }
        } else {
            // 普通 Fallback：必须 implements 目标 Feign 接口
            if (!feignClientInterface.isAssignableFrom(fallbackClass)) {
                throw new IllegalStateException(String.format(
                        "@FeignFallback 校验失败：类 %s 声明兜底 %s，但未实现该接口",
                        fallbackClass.getName(), feignClientInterface.getName()));
            }
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClassName);
        String beanName = BEAN_NAME_GENERATOR.generateBeanName(candidate, registry);
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    /**
     * 解析 fallback / fallbackFactory 类对应的 Feign 客户端接口。
     *
     * <p>解析顺序：{@link FeignFallback#value()} 显式指定则直接采用（目标必须标注
     * {@code @FeignClient}）；未指定则自动推断——FallbackFactory 解析泛型参数 T、普通 Fallback
     * 扫描直接实现接口中标注 {@code @FeignClient} 的（仅直接实现，不递归父接口）。
     * 推断失败或目标不构成 Feign 契约时抛 {@link IllegalStateException}，要求显式指定。
     *
     * <p>注解读取用 {@link AnnotatedElementUtils#findMergedAnnotation}，支持组合注解（meta-annotation）。
     */
    private Class<?> resolveFeignClientInterface(Class<?> fallbackClass, FeignFallback annotation, boolean isFallbackFactory) {
        // 1. 显式优先：用户在注解里写了 value() 就直接用
        if (annotation.value() != void.class) {
            Class<?> target = annotation.value();
            // 校验：用户指定的目标接口必须真的是 @FeignClient
            if (AnnotatedElementUtils.findMergedAnnotation(target, FeignClient.class) == null) {
                throw new IllegalStateException(String.format(
                        "@FeignFallback 显式指定的目标 %s 未标注 @FeignClient，请确认配置是否正确", target.getName()));
            }
            return target;
        }

        // 2. 自动推断
        if (isFallbackFactory) {
            // FallbackFactory：从 FallbackFactory<T> 解析 T
            Class<?> factoryT = GenericTypeResolver.resolveTypeArgument(fallbackClass, FallbackFactory.class);
            if (factoryT == null) {
                throw new IllegalStateException(String.format(
                        "@FeignFallback 自动推断失败：类 %s 是 FallbackFactory 实现但无法解析泛型参数。" +
                        "请显式声明泛型参数（避免使用原始类型 FallbackFactory），或显式指定 @FeignFallback(XxxFeignClient.class)",
                        fallbackClass.getName()));
            }
            if (AnnotatedElementUtils.findMergedAnnotation(factoryT, FeignClient.class) == null) {
                throw new IllegalStateException(String.format(
                        "@FeignFallback 自动推断失败：类 %s 的泛型参数 %s 未标注 @FeignClient，" +
                        "不构成 Feign 契约。请确认泛型参数是否正确，或显式指定 @FeignFallback(XxxFeignClient.class)",
                        fallbackClass.getName(), factoryT.getName()));
            }
            return factoryT;
        }

        // 普通 Fallback：扫描直接实现的接口，找 @FeignClient 标注的
        List<Class<?>> feignInterfaces = new ArrayList<>();
        for (Class<?> iface : fallbackClass.getInterfaces()) {
            if (AnnotatedElementUtils.findMergedAnnotation(iface, FeignClient.class) != null) {
                feignInterfaces.add(iface);
            }
        }

        if (feignInterfaces.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "@FeignFallback 自动推断失败：类 %s 未实现任何 @FeignClient 接口。" +
                    "请确保该类直接 implements 对应的 Feign 契约接口，或显式指定 @FeignFallback(XxxFeignClient.class)",
                    fallbackClass.getName()));
        }

        if (feignInterfaces.size() > 1) {
            throw new IllegalStateException(String.format(
                    "@FeignFallback 自动推断失败：类 %s 实现了多个 @FeignClient 接口 %s，存在歧义。" +
                    "请显式指定 @FeignFallback(XxxFeignClient.class) 指明兜底目标",
                    fallbackClass.getName(), feignInterfaces));
        }

        return feignInterfaces.get(0);
    }
}
