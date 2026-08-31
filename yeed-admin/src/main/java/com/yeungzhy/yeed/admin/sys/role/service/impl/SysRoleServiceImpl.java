package com.yeungzhy.yeed.admin.sys.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRoleMenuGrantDTO;
import com.yeungzhy.yeed.admin.sys.role.dto.SysRolePageDTO;
import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.admin.sys.role.entity.SysRoleMenu;
import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMapper;
import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMenuMapper;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleConvert;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleService;
import com.yeungzhy.yeed.admin.sys.role.service.SysRoleSorts;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRolePageVO;
import com.yeungzhy.yeed.admin.sys.role.vo.SysRoleVO;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUserRole;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserRoleMapper;
import com.yeungzhy.yeed.common.core.enums.BuiltinRoleEnum;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.request.StatusRequest;
import com.yeungzhy.yeed.common.core.result.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统角色 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:55:14
 */
@Slf4j
@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Resource
    private SysRoleSorts sysRoleSorts;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private SysRoleConvert sysRoleConvert;


    @Override
    public Long save(SysRoleDTO dto) {
        BizAssert.notBlank(dto.getRoleName(), "角色名称不能为空");
        BizAssert.notBlank(dto.getRoleCode(), "角色编码不能为空");
        BizAssert.isFalse(BuiltinRoleEnum.isBuiltin(dto.getRoleCode()), "角色编码与内置角色冲突，请更换");

        // 角色编码是业务唯一标识
        checkRoleCodeUnique(dto.getRoleCode(), null);

        // DTO -> Entity：同名字段由 MapStruct 自动映射
        SysRole entity = sysRoleConvert.toEntity(dto);
        sysRoleMapper.insert(entity);
        return entity.getId();
    }


    @Override
    public void update(SysRoleDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        BizAssert.notBlank(dto.getRoleName(), "角色名称不能为空");
        BizAssert.notBlank(dto.getRoleCode(), "角色编码不能为空");

        SysRole dbEntity = sysRoleMapper.selectById(dto.getId());
        BizAssert.notNull(dbEntity, "记录不存在");
        // 内置角色身份由 roleCode 白名单判定，禁改编码，否则 isBuiltin 判定失效
        BizAssert.isFalse(dbEntity.isBuiltin() && !Objects.equals(dbEntity.getRoleCode(), dto.getRoleCode()),
                "内置角色禁止修改角色编码");
        checkRoleCodeUnique(dto.getRoleCode(), dto.getId());

        // DTO -> Entity：id 与业务字段均自动映射
        SysRole entity = sysRoleConvert.toEntity(dto);
        sysRoleMapper.updateById(entity);
    }

    @Override
    public void updateStatus(StatusRequest dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        BizAssert.notNull(dto.getStatus(), "目标状态不能为空");
        SysRole sysRole = sysRoleMapper.selectById(dto.getId());
        BizAssert.notNull(sysRole, "记录不存在");
        // 内置角色禁改
        BizAssert.isFalse(sysRole.isBuiltin(), "内置角色禁止修改状态");
        SysRole updateEntity = SysRole.builder()
                .id(dto.getId())
                .status(dto.getStatus())
                .build();
        sysRoleMapper.updateById(updateEntity);
    }

    @Override
    public SysRoleVO detail(Long id) {
        SysRole entity = sysRoleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return sysRoleConvert.toVO(entity);
    }


    @Override
    public PageResult<SysRolePageVO> page(SysRolePageDTO dto) {
        LambdaQueryWrapper<SysRole> lambdaQuery = Wrappers.<SysRole>lambdaQuery()
                .like(StringUtils.isNotBlank(dto.getRoleName()), SysRole::getRoleName, dto.getRoleName())
                .like(StringUtils.isNotBlank(dto.getRoleCode()), SysRole::getRoleCode, dto.getRoleCode())
                .eq(Objects.nonNull(dto.getStatus()), SysRole::getStatus, dto.getStatus());

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysRoleSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return sysRoleMapper.selectPageVO(dto, lambdaQuery, sysRoleConvert::toPageVO);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantMenus(SysRoleMenuGrantDTO dto) {
        BizAssert.notNull(dto.getRoleId(), "角色ID不能为空");
        BizAssert.notEmpty(dto.getMenuIds(), "菜单ID集合不能为空");
        // 角色必须存在
        BizAssert.notNull(sysRoleMapper.selectById(dto.getRoleId()), "角色不存在");
        // 菜单必须全部存在（去重后数量比对；菜单为逻辑删除表，selectCount 自动滤已删数据）
        List<Long> menuIds = dto.getMenuIds().stream().distinct().toList();
        Long menuCount = sysMenuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery().in(SysMenu::getId, menuIds));
        BizAssert.isTrue(menuCount == menuIds.size(), "存在不存在的菜单ID");

        // 全量覆盖式授权：先清空旧关联，再批量写入新关联（关联表无审计字段，物理删插即可）
        sysRoleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, dto.getRoleId()));
        // 服务端强制补链（数据不变量）：沿 parentId 上溯补全祖先节点，菜单树完整性不依赖前端联动提交；
        // 前端已联动提交完整集时补链幂等无害，前端绕过/漏传时兜底。脏数据断链即停止，不插入悬空关联
        Map<Long, Long> parentIdMap = sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery().select(SysMenu::getId, SysMenu::getParentId))
                .stream()
                .collect(Collectors.toMap(SysMenu::getId, SysMenu::getParentId));
        // LinkedHashSet 保序去重：先授权菜单，再按提交顺序补其祖先
        Set<Long> allMenuIds = new LinkedHashSet<>(menuIds);
        for (Long menuId : menuIds) {
            Long parentId = parentIdMap.get(menuId);
            Set<Long> visited = new HashSet<>();
            while (parentId != null && visited.add(parentId)) {
                // 父级为根(0)或不在菜单表中（脏数据断链）：停止补链
                if (SysMenu.isRoot(parentId) || !parentIdMap.containsKey(parentId)) {
                    break;
                }
                allMenuIds.add(parentId);
                parentId = parentIdMap.get(parentId);
            }
        }
        // 构造关联列表后批量插入，一条 SQL 写入（菜单数量可能较大，避免循环单条插入的 N 次网络往返）
        List<SysRoleMenu> roleMenus = allMenuIds.stream()
                .map(menuId -> SysRoleMenu.builder()
                        .roleId(dto.getRoleId())
                        .menuId(menuId)
                        .build())
                .toList();
        sysRoleMenuMapper.insert(roleMenus);
    }


    @Override
    public List<Long> listMenuIdsByRole(Long roleId) {
        BizAssert.notNull(roleId, "角色ID不能为空");
        return sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        SysRole entity = sysRoleMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // 内置角色是系统安全基座，禁止删除
        BizAssert.isFalse(entity.isBuiltin(), "内置角色禁止删除");
        // 已绑定用户的角色禁止删除：需先解除该角色下所有用户的授权，避免授权数据悬空
        Long bindCount = sysUserRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
        BizAssert.isTrue(bindCount == 0, "角色已绑定用户，请先解除该角色下的所有用户后再删除");
        // 级联清理角色-菜单授权关联，避免残留孤儿数据
        sysRoleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
        sysRoleMapper.deleteByIdAutoFill(id);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        // 批量中任一内置角色即整体拒绝，防止绕过单条删除保护
        List<SysRole> roles = sysRoleMapper.selectByIds(ids);
        boolean containsBuiltin = roles.stream().anyMatch(SysRole::isBuiltin);
        BizAssert.isFalse(containsBuiltin, "内置角色禁止删除");
        // 任一角色已绑定用户即整体拒绝：需先解除相关角色的用户授权，避免授权数据悬空
        Long bindCount = sysUserRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getRoleId, ids));
        BizAssert.isTrue(bindCount == 0, "存在已绑定用户的角色，请先解除相关角色的用户授权后再删除");
        // 级联清理角色-菜单授权关联，避免残留孤儿数据
        sysRoleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, ids));
        // 批量逻辑删除：空集合不触库，超量自动分片，删除人自动填充
        sysRoleMapper.deleteByIdsAutoFill(ids);
    }


    /**
     * 校验角色编码唯一（更新时排除自身）
     */
    private void checkRoleCodeUnique(String roleCode, Long excludeId) {
        boolean exists = sysRoleMapper.existsByCondition(q -> {
            q.eq(SysRole::getRoleCode, roleCode);
            if (excludeId != null) {
                q.ne(SysRole::getId, excludeId);
            }
        });
        BizAssert.isTrue(!exists, "角色编码已存在");
    }

}
