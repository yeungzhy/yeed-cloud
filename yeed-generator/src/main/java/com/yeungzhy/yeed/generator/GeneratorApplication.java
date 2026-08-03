package com.yeungzhy.yeed.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.builder.CustomFile;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import com.yeungzhy.yeed.common.model.BaseEntity;
import org.apache.ibatis.annotations.Mapper;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GeneratorApplication {

    // ==================== 开发者配置区（请根据实际需求修改以下配置项） ====================

    // ---------- 数据库连接 ----------
    private static final String DB_URL = "jdbc:mysql://192.168.22.155:3306/yeed?useUnicode=true&characterEncoding=UTF-8&useSSL=false";
    private static final String DB_USERNAME = "yeed";
    private static final String DB_PASSWORD = "yeed123";

    // ---------- 项目级常量（一般无需修改） ----------
    /** 表前缀：生成类名时去除该前缀（yeed_sys_user -> SysUser） */
    private static final String TABLE_PREFIX = "yeed_";
    /** 代码输出到的子模块名（项目根下的同级模块） */
    private static final String OUTPUT_MODULE = "yeed-admin";
    /** 应用基础包：完整包与 XML 子目录均以此为基准派生，使 XML 目录与 Java 包结构保持一致 */
    private static final String BASE_PACKAGE = "com.yeungzhy.yeed.admin";


    // ---------- 每次生成按需修改 ----------
    /** 作者名（写入类注释 @author） */
    private static final String AUTHOR = "yeungzhy";
    /** 需要生成代码的表名，支持多张表 */
    private static final String[] TABLE_NAMES = {"yeed_sys_user"};

    /** 业务域：决定包层级（com.yeungzhy.yeed.admin.<domain>，如 sys / nps），与 MODULE_NAME 共同决定完整包 */
    private static final String DOMAIN = "sys";
    /** 业务模块名：决定生成的包路径（如 user -> com.yeungzhy.yeed.admin.sys.user） */
    private static final String MODULE_NAME = "user";


    // ==================================================================================
    public static void main(String[] args) {

        File outputModuleDir = new File(locateProjectRoot(), OUTPUT_MODULE);
        String javaOutputDir = new File(outputModuleDir, "src/main/java").getAbsolutePath();

        // 派生父包：由 BASE_PACKAGE + DOMAIN 拼接，避免手写长包名与 BASE_PACKAGE 层级不一致
        String packageParent = BASE_PACKAGE + "." + DOMAIN;
        // 完整包：com.yeungzhy.yeed.admin.sys.user
        String fullPackage = packageParent + "." + MODULE_NAME;
        // XML 子目录与 Java 包结构对齐：完整包相对于 BASE_PACKAGE 的部分作为子路径，
        // 例如 com.yeungzhy.yeed.admin.sys.user 相对于 com.yeungzhy.yeed.admin -> sys/user，输出到 mapper/sys/user/；
        // mapper-locations 用 classpath*:/mapper/**/*.xml 递归匹配，任意层级均可被扫描。
        String xmlSubPath = fullPackage.substring((BASE_PACKAGE + ".").length()).replace('.', '/');
        String xmlOutputDir = new File(outputModuleDir, "src/main/resources/mapper/" + xmlSubPath).getAbsolutePath();

        // 生成前预览解析结果，便于肉眼核对层级/路径是否正确
        System.out.println("==================== 代码生成配置预览 ====================");
        System.out.println("作者       : " + AUTHOR);
        System.out.println("表名       : " + String.join(", ", TABLE_NAMES));
        System.out.println("完整包     : " + fullPackage);
        System.out.println("XML 目录   : src/main/resources/mapper/" + xmlSubPath);
        System.out.println("Java 输出  : " + javaOutputDir);
        System.out.println("=========================================================");

        FastAutoGenerator.create(DB_URL, DB_USERNAME, DB_PASSWORD)
                // ========== 1. 全局配置 ==========
                .globalConfig(builder -> builder
                        .outputDir(javaOutputDir)
                        .author(AUTHOR)
                        .disableOpenDir()                   // 禁止自动打开输出目录（默认 true）
                        .dateType(DateType.TIME_PACK)       // 时间类型策略，默认 TIME_PACK
                        .commentDate("yyyy-MM-dd HH:mm:ss") // 注释日期格式
                )
                // ========== 2. 包配置 ==========
                .packageConfig(builder -> builder
                        .parent(packageParent)
                        .moduleName(MODULE_NAME)
                        .entity("entity")
                        .service("service")
                        .serviceImpl("service.impl")
                        .mapper("mapper")
                        .controller("controller")
                        .xml("mapper.xml")                  // XML 命名空间包名
                        .pathInfo(Collections.singletonMap(OutputFile.xml, xmlOutputDir))
                )
                // ========== 3. 策略配置（核心） ==========
                // 自定义模板文件位于 src/main/resources/templates/ 下
                .strategyConfig(builder -> builder
                        // 表匹配（指定表名）
                        .addInclude(TABLE_NAMES)
                        .addTablePrefix(TABLE_PREFIX)

                        // ----- Entity 策略 -----
                        .entityBuilder()
                        .javaTemplate("/templates/yeed-entity.java.vm")   // 自定义实体模板
                        .disableSerialVersionUID()          // 不生成 serialVersionUID（项目使用 JSON 序列化，需要时手动添加）
                        .enableChainModel()                 // 开启链式模型（@Accessors(chain=true) + 导入）
                        // 不调用 enableTableFieldAnnotation()：数据库字段为标准下划线命名，可直接转驼峰，无需 @TableField 注解
                        .naming(NamingStrategy.underline_to_camel)       // 表名转驼峰
                        .columnNaming(NamingStrategy.underline_to_camel) // 字段名转驼峰
                        .superClass(BaseEntity.class)                    // 继承父类
                        .addSuperEntityColumns(                          // 父类公共字段
                                "id",
                                "create_by", "create_time",
                                "update_by", "update_time",
                                "delete_by", "delete_time",
                                "version", "extra"
                        )

                        // ----- Controller 策略 -----
                        .controllerBuilder()
                        .template("/templates/yeed-controller.java.vm") // 自定义 Controller 模板
                        .enableRestStyle()                  // 生成 @RestController
                        .enableHyphenStyle()                // 开启驼峰转连字符（可选）

                        // ----- Service 策略 -----
                        .serviceBuilder()
                        .formatServiceFileName("%sService")                          // 接口名去掉默认 I 前缀（默认 I%sService -> %sService）
                        .serviceTemplate("/templates/yeed-service.java.vm")         // 自定义 Service 接口模板
                        .serviceImplTemplate("/templates/yeed-serviceImpl.java.vm") // 自定义 Service 实现模板

                        // ----- Mapper 策略 -----
                        .mapperBuilder()
                        .mapperTemplate("/templates/yeed-mapper.java.vm")   // 自定义 Mapper 模板
                        .mapperXmlTemplate("/templates/yeed-mapper.xml.vm") // 自定义 Mapper XML 模板（为 extra 等 json 列生成 typeHandler）
                        .mapperAnnotation(Mapper.class)     // 开启 @Mapper 注解
                        .enableBaseResultMap()              // 启用 BaseResultMap（通用查询映射结果）
                        // 不启用 BaseColumnList：MyBatis-Plus BaseMapper 已提供通用列，XML 无需重复列清单
                )
                // ========== 3.5 注入配置（自定义生成 DTO/VO） ==========
                .injectionConfig(builder -> builder
                        // 注入 dto/vo 包名，供模板 ${dtoPackage} / ${voPackage} 使用
                        .customMap(Map.of(
                                "dtoPackage", fullPackage + ".dto",
                                "voPackage", fullPackage + ".vo"
                        ))
                        // 自定义输出文件：fileName 作为 entityName 后缀拼接（生成 XxxDTO/XxxVO），packageName 决定输出子包
                        .customFile(List.of(
                                new CustomFile.Builder()
                                        .fileName("DTO.java")
                                        .templatePath("/templates/yeed-dto.java.vm")
                                        .packageName("dto")
                                        .build(),
                                new CustomFile.Builder()
                                        .fileName("VO.java")
                                        .templatePath("/templates/yeed-vo.java.vm")
                                        .packageName("vo")
                                        .build()
                        ))
                )
                // ========== 4. 模板引擎 ==========
                .templateEngine(new VelocityTemplateEngine())
                .execute();

        System.out.println("生成成功");
        // 作为一次性代码生成工具，生成完成后直接强制 JVM 退出
        System.exit(0);
    }

    /**
     * 定位项目根目录。
     * 通过当前类的编译输出位置（target/classes 或 out/production/...）逐级向上查找，
     * 同时包含 yeed-generator 与输出模块（{@link #OUTPUT_MODULE}）的目录即为项目根。
     * 这样无论从 IDEA、命令行还是哪个工作目录运行都能正确定位。
     */
    private static File locateProjectRoot() {
        try {
            File current = new File(GeneratorApplication.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            while (current != null) {
                if (new File(current, "yeed-generator").isDirectory()
                        && new File(current, OUTPUT_MODULE).isDirectory()) {
                    return current;
                }
                current = current.getParentFile();
            }
            throw new IllegalStateException("无法定位项目根目录，请检查 yeed-generator 与 " + OUTPUT_MODULE + " 是否为同级模块");
        } catch (URISyntaxException e) {
            throw new IllegalStateException("无法定位项目根目录", e);
        }
    }

}
