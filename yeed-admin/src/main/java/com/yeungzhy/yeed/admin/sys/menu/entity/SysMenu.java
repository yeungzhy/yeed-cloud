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

import java.beans.Transient;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /** 父菜单ID，0-顶级菜单 */
    private Long parentId;
    /** 菜单名称 */
    private String menuName;
    /** 菜单类型（目录/菜单/按钮） */
    private MenuTypeEnum menuType;
    /** 路由地址（目录/菜单页面对应前端路由，按钮可为空） */
    private String path;
    /** 权限标识符（如 sys:user:list，角色授权时使用） */
    private String perms;
    /** 显示排序 */
    private Integer sort;
    /** 是否可见（侧边栏显示/隐藏） */
    private MenuVisibleEnum visible;


    // ==================== 业务常量 ====================
    /** 根菜单的 parentId 标识 */
    public static final Long ROOT_PARENT_ID = 0L;


    // ==================== 充血方法（仅承载业务规则） ====================

    /**
     * 目标父级是否为顶级（null 或 0）
     * <p>供树构建（root 判定）、父级合法性校验等"对值判断"场景复用，单一实现体。
     */
    public static boolean isRoot(Long parentId) {
        return parentId == null || ROOT_PARENT_ID.equals(parentId);
    }

    /**
     * 当前菜单是否为顶级菜单（parentId 为 null 或 0）
     * <p>布尔 getter 命名（isXxx）会被 Jackson 识别为属性而序列化，
     * 用 JDK 标准 {@link Transient} 声明"非持久化"忽略，避免 POJO 依赖具体 JSON 框架注解。
     */
    @Transient
    public boolean isRoot() {
        return isRoot(this.parentId);
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
        // 顶级父级（null/0）永远安全
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
