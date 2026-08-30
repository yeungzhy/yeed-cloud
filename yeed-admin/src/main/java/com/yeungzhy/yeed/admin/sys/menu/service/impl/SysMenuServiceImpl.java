package com.yeungzhy.yeed.admin.sys.menu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuMoveDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuPageDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuSaveDTO;
import com.yeungzhy.yeed.admin.sys.menu.dto.SysMenuUpdateDTO;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.admin.sys.menu.service.MenuCacheReloader;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuConvert;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuService;
import com.yeungzhy.yeed.admin.sys.menu.service.SysMenuSorts;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuPageVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuTreeVO;
import com.yeungzhy.yeed.admin.sys.menu.vo.SysMenuVO;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.support.TreeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统菜单权限表 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:30
 */
@Slf4j
@Service
public class SysMenuServiceImpl implements SysMenuService {

    @Resource
    private SysMenuSorts sysMenuSorts;
    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private SysMenuConvert sysMenuConvert;
    @Resource
    private MenuCacheReloader menuCacheReloader;


    @Override
    public Long save(SysMenuSaveDTO dto) {
        BizAssert.notNull(dto.getMenuType(), "菜单类型不能为空");
        BizAssert.notBlank(dto.getMenuName(), "菜单名称不能为空");
        // 按钮 path 语义为调用接口路径（如 /sys/user/list），perms 由 path 派生，单一数据源
        assertButtonPath(dto.getMenuType(), dto.getPath());
        if (dto.getMenuType() == MenuTypeEnum.BUTTON) {
            // 前端按 MVC 风格提交路径变量（{id}），入库统一归一化为 Ant 通配符（*），网关按模式匹配
            dto.setPath(SysMenu.normalizePath(dto.getPath()));
            dto.setPerms(SysMenu.pathToPerm(dto.getPath()));
        }

        // parentId 必填：项目约定仅 0 表示顶级、不存在 null，从入参即杜绝 null 语义
        BizAssert.notNull(dto.getParentId(), "父菜单ID不能为空（顶级传0）");
        // 非顶级菜单：校验父级存在性（新建节点 id 为空，挂到存在的父级下不可能成环，无需防环校验）
        if (!SysMenu.isRoot(dto.getParentId())) {
            BizAssert.notNull(sysMenuMapper.selectById(dto.getParentId()), "父级菜单不存在");
        }
        // 同一父级下菜单名称唯一
        checkMenuNameUnique(dto.getParentId(), dto.getMenuName(), null);

        // DTO -> Entity：同名字段由 MapStruct 自动映射
        SysMenu entity = sysMenuConvert.toEntity(dto);
        sysMenuMapper.insert(entity);
        menuCacheReloader.reload();
        return entity.getId();
    }


    @Override
    public void update(SysMenuUpdateDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        BizAssert.notBlank(dto.getMenuName(), "菜单名称不能为空");
        BizAssert.notNull(dto.getMenuType(), "菜单类型不能为空");
        // 按钮 path 语义为调用接口路径（如 /sys/user/list），perms 由 path 派生，单一数据源
        assertButtonPath(dto.getMenuType(), dto.getPath());
        if (dto.getMenuType() == MenuTypeEnum.BUTTON) {
            // 前端按 MVC 风格提交路径变量（{id}），入库统一归一化为 Ant 通配符（*），网关按模式匹配
            dto.setPath(SysMenu.normalizePath(dto.getPath()));
            dto.setPerms(SysMenu.pathToPerm(dto.getPath()));
        }

        SysMenu dbEntity = sysMenuMapper.selectById(dto.getId());
        BizAssert.notNull(dbEntity, "记录不存在");

        // parentId 必填：项目约定仅 0 表示顶级、不存在 null，从入参即杜绝 null 语义
        BizAssert.notNull(dto.getParentId(), "父菜单ID不能为空（顶级传0）");
        // 目标父级：存在性 + 防自环（直接/间接）
        assertValidParent(dbEntity, dto.getParentId());
        // 同一父级下菜单名称唯一（排除自身）
        checkMenuNameUnique(dto.getParentId(), dto.getMenuName(), dto.getId());

        // DTO -> Entity：id 与业务字段均自动映射
        SysMenu entity = sysMenuConvert.toEntity(dto);
        sysMenuMapper.updateById(entity);
        menuCacheReloader.reload();
    }


    @Override
    public void move(SysMenuMoveDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        // parentId 必填：项目约定仅 0 表示顶级、不存在 null，从入参即杜绝 null 语义
        BizAssert.notNull(dto.getParentId(), "父菜单ID不能为空（顶级传0）");
        SysMenu dbEntity = sysMenuMapper.selectById(dto.getId());
        BizAssert.notNull(dbEntity, "记录不存在");

        // 目标父级：存在性 + 防自环（直接/间接）
        assertValidParent(dbEntity, dto.getParentId());

        // 显式 set parentId：仅更新层级字段（移到顶级传 0，update 不承载 null 语义）
        sysMenuMapper.update(null, Wrappers.<SysMenu>lambdaUpdate()
                .set(SysMenu::getParentId, dto.getParentId())
                .eq(SysMenu::getId, dto.getId()));
        menuCacheReloader.reload();
    }


    @Override
    public SysMenuVO detail(Long id) {
        SysMenu entity = sysMenuMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return sysMenuConvert.toVO(entity);
    }


    @Override
    public PageResult<SysMenuPageVO> page(SysMenuPageDTO dto) {
        LambdaQueryWrapper<SysMenu> lambdaQuery = buildQueryWrapper(dto);

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysMenuSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return sysMenuMapper.selectPageVO(dto, lambdaQuery, sysMenuConvert::toPageVO);
    }


    @Override
    public List<SysMenuTreeVO> tree() {
        // 懒加载自愈：外部清库后首个菜单树请求即重建鉴权缓存（超管进菜单管理页即恢复）
        menuCacheReloader.reloadIfAbsent();
        List<SysMenu> menus = sysMenuMapper.selectList(
                Wrappers.<SysMenu>lambdaQuery().orderByAsc(SysMenu::getSort));
        // 查询侧兜底：存量脏数据环不阻断（迭代式 buildTree 不会栈溢出），仅告警暴露待修数据
        warnIfCycle(menus);
        // Entity -> TreeVO（children 由 TreeUtil 组装，非 Convert 职责）
        List<SysMenuTreeVO> flat = menus.stream().map(sysMenuConvert::toTreeVO).toList();
        return TreeUtil.buildTree(flat,
                SysMenuTreeVO::getParentId,
                SysMenuTreeVO::getId,
                vo -> SysMenu.isRoot(vo.getParentId()),
                SysMenuTreeVO::setChildren);
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        boolean hasChildren = sysMenuMapper.existsByColumn(SysMenu::getParentId, id);
        BizAssert.isTrue(!hasChildren, "存在子菜单，不允许删除");
        sysMenuMapper.deleteByIdAutoFill(id);
        menuCacheReloader.reload();
    }


    @Override
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        boolean hasChildren = sysMenuMapper.existsByCondition(w -> w.in(SysMenu::getParentId, ids));
        BizAssert.isTrue(!hasChildren, "存在子菜单，不允许删除");
        // 批量逻辑删除：空集合不触库，超量自动分片，删除人自动填充
        sysMenuMapper.deleteByIdsAutoFill(ids);
        menuCacheReloader.reload();
    }


    /**
     * 构建查询条件（菜单名称模糊 / 类型、可见性精确匹配）
     */
    private LambdaQueryWrapper<SysMenu> buildQueryWrapper(SysMenuPageDTO dto) {
        return Wrappers.<SysMenu>lambdaQuery()
                .like(StringUtils.isNotBlank(dto.getMenuName()), SysMenu::getMenuName, dto.getMenuName())
                .eq(Objects.nonNull(dto.getMenuType()), SysMenu::getMenuType, dto.getMenuType())
                .eq(Objects.nonNull(dto.getVisible()), SysMenu::getVisible, dto.getVisible());
    }


    /**
     * 校验按钮必须填写调用接口路径
     * <p>按钮（BUTTON）的 path 语义为「调用接口路径」（如 /sys/user/list），
     * 权限码由 path 派生（{@link SysMenu#pathToPerm(String)}），单一数据源，
     * 前端无需传 perms；网关据此 Map&lt;接口路径, 权限码&gt; 做接口鉴权，
     * path 缺失则接口无法被网关管控，故强制必填
     */
    private void assertButtonPath(MenuTypeEnum menuType, String path) {
        if (menuType == MenuTypeEnum.BUTTON) {
            BizAssert.notBlank(path, "按钮类型必须填写调用接口路径（如 /sys/user/list）");
        }
    }


    /**
     * 校验目标父级合法（存在 + 不构成环），update / move 共用
     */
    private void assertValidParent(SysMenu dbEntity, Long targetParentId) {
        // 目标为顶级（0）：无需父级存在性校验，也不可能成环
        if (SysMenu.isRoot(targetParentId)) {
            return;
        }
        BizAssert.notNull(sysMenuMapper.existsByColumn(SysMenu::getId, targetParentId), "父级菜单不存在");
        BizAssert.isTrue(dbEntity.canChangeParentTo(targetParentId, loadParentIdMap()),
                "目标父级不能是当前菜单或其后代（会形成循环依赖）");
    }


    /**
     * 一次性载入全量 id→parentId 映射，供祖先链查环（菜单量小，整表载入可接受）
     */
    private Map<Long, Long> loadParentIdMap() {
        // 项目约定 parentId 不存在 null（0=顶级），全量直载入映射
        return sysMenuMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysMenu::getId, SysMenu::getParentId));
    }


    /**
     * 校验同一父级下菜单名称唯一
     * <p>项目约定 parentId 不存在 null（仅 0 为顶级），直接以入参精确匹配即可，无需归一化。
     */
    private void checkMenuNameUnique(Long parentId, String menuName, Long excludeId) {
        boolean exists = sysMenuMapper.existsByCondition(q -> {
            q.eq(SysMenu::getParentId, parentId)
                    .eq(SysMenu::getMenuName, menuName);
            if (excludeId != null) {
                q.ne(SysMenu::getId, excludeId);
            }
        });
        BizAssert.isTrue(!exists, "同一父级下菜单名称已存在");
    }


    /**
     * 查询侧环检测（防御历史脏数据）
     * <p>沿每个节点的祖先链上追，若再次经过已访问节点说明存在环；
     * 仅告警不阻断——写入侧 {@code assertValidParent} 已拦截新环，
     * 迭代式 TreeUtil 构建也不会栈溢出，这里负责把存量脏数据显性化。
     */
    private void warnIfCycle(List<SysMenu> menus) {
        // 项目约定 parentId 不存在 null，直载入映射（映射缺 id 时 get 返回 null，祖先链自然终止）
        Map<Long, Long> parentIdById = new HashMap<>();
        for (SysMenu m : menus) {
            parentIdById.put(m.getId(), m.getParentId());
        }
        Set<Long> visited = new HashSet<>();
        for (SysMenu m : menus) {
            visited.clear();
            Long cursor = m.getId();
            while (cursor != null && visited.add(cursor)) {
                cursor = parentIdById.get(cursor);
            }
            if (cursor != null) {
                log.warn("菜单数据存在循环引用（节点 {} 祖先链重复），请检查并修复数据库", m.getId());
                return;
            }
        }
    }

}
