package com.yeungzhy.yeed.admin.sys.menu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuVisibleEnum;
import com.yeungzhy.yeed.common.data.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 系统菜单权限表
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "yeed_sys_menu", autoResultMap = true)
public class SysMenu extends BaseEntity {

    /** 父菜单ID，0-顶级菜单（项目约定不存在 null） */
    private Long parentId;
    /** 菜单名称 */
    private String menuName;
    /** 菜单类型（目录/菜单/按钮） */
    private MenuTypeEnum menuType;
    /** 路由地址（目录/菜单对应用户端路由；按钮为调用接口路径 */
    private String path;
    /** 权限标识符按钮由 path 派生 */
    private String perms;
    /** 显示排序 */
    private Integer sort;
    /** 是否可见（侧边栏显示/隐藏） */
    private MenuVisibleEnum visible;


    // ==================== 业务常量 ====================
    /** 根菜单的 parentId 标识 */
    public static final Long ROOT_PARENT_ID = 0L;

    /** 按钮接口路径归一化正则：匹配形如 {id} 的路径变量段（不含 / 与 }，一个变量对位一个路径段） */
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{[^/}]+\\}");


    // ==================== 充血方法（仅承载业务规则） ====================

    /**
     * 目标父级是否为顶级（仅 0）
     * <p>项目约定 parentId 不存在 null，顶级一律存 0；
     * 供树构建（root 判定）、父级合法性校验等"对值判断"场景复用，单一实现体。
     */
    public static boolean isRoot(Long parentId) {
        return ROOT_PARENT_ID.equals(parentId);
    }

    /**
     * 接口路径 → 权限标识符（按钮 path 语义为调用接口路径）
     * <p>与项目权限编码约定一致：去前导 {@code /}、{@code /}→{@code :}
     * （如 {@code /sys/user/list} → {@code sys:user:list}）。按钮的 perms 由此派生，
     * 单一数据源；网关鉴权缓存（接口路径→权限码 Map）构建复用同一转换，两侧天然一致。
     *
     * @param path 调用接口路径（如 /sys/user/list）
     * @return 权限标识符；入参为 null/空白时返回 null
     */
    public static String pathToPerm(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.charAt(0) == '/' ? path.substring(1) : path;
        return normalized.replace('/', ':');
    }

    /**
     * 按钮接口路径归一化：Spring MVC 路径变量 {@code {xxx}} → Ant 通配符 {@code *}
     * <p>前端按 MVC 风格提交接口路径（如 {@code /sys/user/{id}/avatar.svg}），
     * 网关按 Ant 通配符匹配真实请求（如 {@code /sys/user/123/avatar.svg}）；
     * 两者语义差异在此归一：{@code {id}} 表示「一个不含 / 的路径段」，与单个 {@code *} 对位。
     * <p>仅替换形如 {@code {xxx}} 的路径变量段（不含 /），已含 {@code *} 的路径原样返回（幂等），
     * 目录/菜单 path 为用户端路由、不含路径变量，本方法对其无副作用。
     *
     * @param path 按钮调用接口路径（可能含 MVC 路径变量）
     * @return 归一化后的 Ant 模式路径；入参为 null/空白时返回 null
     */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return PATH_VARIABLE_PATTERN.matcher(path).replaceAll("*");
    }

    /**
     * 本实例能否把 parentId 改为 targetParentId（防自环）
     * <p>从目标父级沿祖先链一路向上追，命中自身 id 即成环——天然覆盖两种形态：
     * <ul>
     *   <li>直接自环：targetParentId == 自身 id（把自己挂到自己下面）</li>
     *   <li>间接自环：目标父级是自己后代链上的节点（如挂到自己的孙子下）</li>
     * </ul>
     * <p>入参 parentIdById 由调用方一次性查出全量 id→parentId 映射，避免逐级查库；
     * 内部 visited 防御存量脏数据环导致 while 死循环。
     *
     * @param targetParentId 目标父级 ID
     * @param parentIdById   全量菜单 id → parentId 映射
     * @return true=安全；false=会形成环
     */
    public boolean canChangeParentTo(Long targetParentId, Map<Long, Long> parentIdById) {
        // 顶级父级（0）永远安全
        if (isRoot(targetParentId)) {
            return true;
        }
        // 祖先链上追：从目标父级开始，逐级沿 parentId 向上
        Set<Long> visited = new HashSet<>();
        Long cursor = targetParentId;
        while (cursor != null && visited.add(cursor)) {
            if (getId() != null && getId().equals(cursor)) {
                return false;
            }
            cursor = parentIdById.get(cursor);
        }
        return true;
    }

}
