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
 * Feign 兜底实现注册器：核心扫描与注册逻辑。
 *
 * <p>本类由 {@link EnableFeignFallbacks} 通过 {@code @Import} 导入，在 Spring 容器初始化时执行：
 * <ol>
 *   <li>读取 {@link EnableFeignFallbacks#basePackages()} 确定扫描范围</li>
 *   <li>用 {@link ClassPathScanningCandidateComponentProvider} 扫描带 {@link FeignFallback} 注解的具体类</li>
 *   <li>解析并校验 fallback 类对应的 Feign 客户端接口（显式优先，未指定则自动推断）</li>
 *   <li>将扫描到的类注册为 Bean，供 {@code @FeignClient(fallback = ...)} 引用</li>
 * </ol>
 *
 * <p>本机制借鉴 Spring Cloud OpenFeign 的 {@code FeignClientsRegistrar}，实现思路一致：
 * 自定义注解 + {@link ImportBeanDefinitionRegistrar} + 类路径扫描。
 *
 * <p>{@link ImportBeanDefinitionRegistrar} 的执行时机：在所有 {@code @Configuration} 类解析完成后、
 * Bean 实例化之前。此时可以动态向容器注册 BeanDefinition，比 {@code @Bean} 方法更灵活，
 * 适合"扫描发现 + 批量注册"的场景。
 *
 * @author yeungzhy
 * @since 2026-08-10
 */
public class FeignFallbacksRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    /** beanName 生成器：与 @Component 默认命名规则一致（类名首字母小写） */
    private static final AnnotationBeanNameGenerator BEAN_NAME_GENERATOR = new AnnotationBeanNameGenerator();
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

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

        // 3. 构建类路径扫描器
        //    useDefaultFilters=false：关闭默认过滤器（@Component/@Service 等），只用我们自己的 @FeignFallback 过滤器
        //    这样不会误扫到其他 @Component，精准定位 fallback 类
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
     * 解析扫描包：优先 basePackages，其次 value（支持 @EnableFeignFallbacks("com.xxx") 简写），
     * 都未指定则用标注类所在包。
     *
     * <p>与 Spring Cloud OpenFeign 的 {@code FeignClientsRegistrar.getBasePackages} 保持一致，
     * 让用户可以用 {@code @EnableFeignFallbacks("com.yeungzhy.yeed.api")} 或
     * {@code @EnableFeignFallbacks(basePackages = "com.yeungzhy.yeed.api")} 两种写法，效果相同。
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
     * <p>支持两种标注场景：
     * <ul>
     *   <li><b>普通 Fallback 类</b>：直接 implements 目标 {@code @FeignClient} 接口</li>
     *   <li><b>FallbackFactory 类</b>：implements {@link FallbackFactory FallbackFactory&lt;T&gt;}，T 为目标
     *       {@code @FeignClient} 接口。Factory 在 {@code create(Throwable)} 中能拿到原始异常，
     *       便于记录错误日志、区分失败原因。</li>
     * </ul>
     *
     * <p>注册前会解析并校验兜底目标 Feign 客户端接口：
     * <ol>
     *   <li>若 {@link FeignFallback#value()} 显式指定，直接采用（向后兼容）</li>
     *   <li>否则自动推断：
     *       <ul>
     *         <li>FallbackFactory：解析泛型参数 T，T 即为目标接口；并校验 T 标注了 {@code @FeignClient}</li>
     *         <li>普通 Fallback：扫描实现的接口，找出被 {@code @FeignClient} 标注的接口
     *             <ul>
     *               <li>0 个 → 抛 {@link IllegalStateException}（未实现任何 Feign 契约）</li>
     *               <li>1 个 → 推断成功</li>
     *               <li>多个 → 抛 {@link IllegalStateException}（歧义，要求显式指定）</li>
     *             </ul>
     *         </li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>无论显式还是推断得到，均强校验：
     * <ul>
     *   <li>普通 Fallback：必须 implements 目标 Feign 接口</li>
     *   <li>FallbackFactory：泛型参数 T 必须是目标 Feign 接口，且 Factory 实现 FallbackFactory<T></li>
     * </ul>
     * 校验在启动期执行（fail-fast），无运行时开销。
     *
     * <p>beanName 使用 {@link AnnotationBeanNameGenerator}（类名首字母小写），
     * 与 {@code @Component} 的默认命名规则一致，便于排查问题。
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
            // FallbackFactory：校验泛型参数解析结果与 Factory 实现一致
            // GenericTypeResolver 已在解析阶段校验了 T 存在且 T 标注 @FeignClient，
            // 这里补一道：确认 fallbackClass 确实是 FallbackFactory<T> 的实现
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

        // 注册为 Bean：genericBeanDefinition 创建一个无参构造的 RootBeanDefinition
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClassName);
        String beanName = BEAN_NAME_GENERATOR.generateBeanName(candidate, registry);
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    /**
     * 解析 fallback / fallbackFactory 类对应的 Feign 客户端接口。
     *
     * <p>解析顺序：
     * <ol>
     *   <li>{@link FeignFallback#value()} 显式指定 → 直接采用（同时校验指定类型标注了 {@code @FeignClient}）</li>
     *   <li>未指定 → 分支处理
     *       <ul>
     *         <li>isFallbackFactory：用 {@link GenericTypeResolver} 解析 {@code FallbackFactory<T>} 的泛型参数 T。
     *             T 必须标注 {@code @FeignClient}，否则抛异常（意味着 T 根本不是 Feign 契约）</li>
     *         <li>普通 Fallback：扫描 {@code fallbackClass.getInterfaces()} 直接实现的接口中被
     *             {@code @FeignClient} 标注的接口。仅扫直接实现，不递归父接口。</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>自动推断失败时抛 {@link IllegalStateException}，要求用户改用显式声明。
     * 使用 {@link AnnotatedElementUtils#findMergedAnnotation} 支持 Spring 组合注解（meta-annotation）。
     *
     * @param fallbackClass     标注了 {@link FeignFallback} 的具体类
     * @param annotation        fallbackClass 上的 {@link FeignFallback} 注解
     * @param isFallbackFactory fallbackClass 是否为 {@link FallbackFactory} 的实现
     * @return 解析出的 Feign 客户端接口（一定标注了 {@code @FeignClient}）
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
