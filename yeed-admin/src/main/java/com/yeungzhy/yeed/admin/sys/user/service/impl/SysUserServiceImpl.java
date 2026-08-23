package com.yeungzhy.yeed.admin.sys.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.common.core.enums.BuiltinRoleEnum;
import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMapper;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserRoleGrantDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserUpdateDTO;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUser;
import com.yeungzhy.yeed.admin.sys.user.entity.SysUserRole;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserMapper;
import com.yeungzhy.yeed.admin.sys.user.mapper.SysUserRoleMapper;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserConvert;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.admin.sys.user.service.SysUserSorts;
import com.yeungzhy.yeed.admin.sys.user.vo.SysUserVO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.common.core.crypto.BlindIndexProvider;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import com.yeungzhy.yeed.common.core.support.TreeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 系统用户 服务实现类
 *
 * @author yeungzhy
 * @since 2026-08-13 06:48:01
 */
@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {
    /* 几乎只有这里用到单向加密密码,就不@Bean注册到容器了 */
    private final Argon2PasswordEncoder argon2PwdEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Resource
    private SysUserSorts sysUserSorts;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysUserConvert sysUserConvert;
    @Resource
    private BlindIndexProvider emailBlindIndex;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysMenuMapper sysMenuMapper;


    @Override
    public Long save(SysUserAddDTO dto) {
        String username = dto.getUsername();
        BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getUsername, username), "系统登录名已存在");

        String employeeNo = dto.getEmployeeNo();
        BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getEmployeeNo, employeeNo), "工号已存在");

        String plaintextEmail = dto.getEmail();
        String emailBidx = emailBlindIndex.generateHex(plaintextEmail);
        BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getEmailBidx, emailBidx), "邮箱已存在");

        SysUser sysUser = SysUser.builder()
                .realName(dto.getRealName())
                .username(username)
                .employeeNo(employeeNo)
                .password(argon2PwdEncoder.encode(dto.getPassword()))
                // email 明文入库，由 FieldCryptoInterceptor 在写库前自动 AES 加密
                .email(plaintextEmail)
                .emailBidx(emailBidx)
                .build();

        sysUserMapper.insert(sysUser);
        return sysUser.getId();
    }


    @Override
    public void update(SysUserUpdateDTO dto) {
        BizAssert.notNull(dto.getId(), "ID 不能为空");
        SysUser sysUser = sysUserMapper.selectById(dto.getId());
        BizAssert.notNull(sysUser, "用户不存在");
        BizAssert.isTrue(argon2PwdEncoder.matches(dto.getPassword(), sysUser.getPassword()), "密码不正确");

        SysUser updateEntity = SysUser.builder().id(dto.getId()).build();

        String username = dto.getUsername();
        if (StringUtils.isNotEmpty(username)) {
            BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getUsername, username), "用户名已存在");
            updateEntity.setUsername(username);
        }
        String email = dto.getEmail();
        if (StringUtils.isNotEmpty(email)) {
            // 加密的敏感字段, 需要用盲索引定位
            String emailBidx = emailBlindIndex.generateHex(email);
            BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getEmailBidx, emailBidx), "邮箱已存在");

            // email 明文入库，由 FieldCryptoInterceptor 在写库前自动 AES 加密
            updateEntity.setEmail(email);
            updateEntity.setEmailBidx(emailBidx);
        }
        sysUserMapper.updateById(updateEntity);
    }


    @Override
    public SysUserVO detail(Long id) {
        SysUser entity = sysUserMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        // Entity -> VO：同名字段由 MapStruct 自动映射
        return sysUserConvert.toVO(entity);
    }


    @Override
    public PageResult<SysUserVO> page(SysUserPageDTO dto) {
        LambdaQueryWrapper<SysUser> lambdaQuery = Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getEmail, SysUser::getStatus, SysUser::getCreateTime)
                .like(StringUtils.isNotEmpty(dto.getUsername()), SysUser::getUsername, dto.getUsername())
                .eq(Objects.nonNull(dto.getStatus()), SysUser::getStatus, dto.getStatus())
                .between(dto.hasCreateTimeRange(), SysUser::getCreateTime, dto.getCreateTimeStart(), dto.getCreateTimeEnd());
        /*
         * 邮箱查询条件需要用邮箱信息生成盲索引进行查询。
         * 注意：不能用 .eq(condition, column, generateHex(...))
         * generateHex 会在 eq 的 condition 判断之前执行，空邮箱会触发 BlindIndexProvider 的 null 校验
         * 该开关只短路 SQL 拼接，不短路实参求值；对带副作用/会抛异常的实参，必须用 if 守卫。
         */
        String email = dto.getEmail();
        if (StringUtils.isNotEmpty(email)) {
            lambdaQuery.eq(SysUser::getEmailBidx, emailBlindIndex.generateHex(email));
        }

        // 应用排序：先单字段 → 再多字段(顺序敏感)；默认降序；白名单外字段静默忽略
        sysUserSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());

        return sysUserMapper.selectPageVO(dto, lambdaQuery, sysUserConvert::toVO);
    }


    @Override
    public LoginUserInfo verify(UserVerifyDTO dto) {
        BizAssert.notBlank(dto.getAccount(), "账号不能为空");
        BizAssert.notBlank(dto.getPassword(), "密码不能为空");

        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, EnableStatusEnum.ENABLED)
                .and(w ->
                        w.eq(SysUser::getUsername, dto.getAccount())
                         .or()
                         .eq(SysUser::getEmployeeNo, dto.getAccount())));

        BizAssert.notNull(user, "账号不存在或已被禁用");
        BizAssert.isTrue(argon2PwdEncoder.matches(dto.getPassword(), user.getPassword()), "密码错误");

        // 装配身份包：角色编码 + 权限标识（菜单树由 listMenusByUserId 单独装配，会话不承载前端渲染数据）
        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(user.getId());
        // 超管代码级短路：不依赖数据库授权配置（权限配置被改坏仍可登录修复），菜单行取全量
        boolean superAdmin = roleCodes.contains(BuiltinRoleEnum.SUPER_ADMIN.getRoleCode());

        // perms：含按钮 type=3，perms 非空、去重
        List<String> perms = selectMenuRows(user.getId(), superAdmin).stream()
                .map(SysMenu::getPerms)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .toList();

        return new LoginUserInfo()
                .setUserId(user.getId())
                .setRealName(user.getRealName())
                .setUsername(user.getUsername())
                .setEmployeeNo(user.getEmployeeNo())
                .setStatus(user.getStatus())
                .setRoleCodes(roleCodes)
                .setPerms(perms);
    }


    @Override
    public List<MenuTreeInfo> listMenusByUserId(Long userId) {
        BizAssert.notNull(userId, "用户ID不能为空");

        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(userId);
        // 超管代码级短路：不依赖数据库授权配置（权限配置被改坏仍可登录修复），菜单行取全量
        boolean superAdmin = roleCodes.contains(BuiltinRoleEnum.SUPER_ADMIN.getRoleCode());

        // 菜单树：仅目录/菜单节点建树（按钮不承载路由），根判定仅 0（与 SysMenu.isRoot 约定一致）
        return TreeUtil.buildTree(
                selectMenuRows(userId, superAdmin).stream()
                        .filter(m -> MenuTypeEnum.DIRECTORY == m.getMenuType() || MenuTypeEnum.MENU_PAGE == m.getMenuType())
                        .map(sysUserConvert::toMenuTreeInfo)
                        .toList(),
                MenuTreeInfo::getParentId,
                MenuTreeInfo::getId,
                m -> SysMenu.isRoot(m.getParentId()),
                MenuTreeInfo::setChildren);
    }

    /**
     * 查询用户可访问的菜单行（超管取全量），供 perms 推导与菜单树装配共用
     *
     * @param userId     用户 ID
     * @param superAdmin 是否超管（超管短路授权配置，取全量菜单行）
     * @return 排序后的菜单行
     */
    private List<SysMenu> selectMenuRows(Long userId, boolean superAdmin) {
        return superAdmin
                ? sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery().orderByAsc(SysMenu::getSort))
                : sysUserMapper.selectMenusByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantRoles(SysUserRoleGrantDTO dto) {
        BizAssert.notNull(dto.getUserId(), "用户ID不能为空");
        BizAssert.notEmpty(dto.getRoleIds(), "角色ID集合不能为空");
        // 用户必须存在，防止授权到不存在的用户上
        BizAssert.notNull(sysUserMapper.selectById(dto.getUserId()), "用户不存在");
        // 角色必须全部存在（去重后数量比对；角色为逻辑删除表，selectCount 自动滤已删数据）
        List<Long> roleIds = dto.getRoleIds().stream().distinct().toList();
        Long roleCount = sysRoleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().in(SysRole::getId, roleIds));
        BizAssert.isTrue(roleCount == roleIds.size(), "存在不存在的角色ID");

        // 全量覆盖式授权：先清空旧关联，再批量写入新关联（关联表无审计字段，物理删插即可）
        sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, dto.getUserId()));
        List<SysUserRole> userRoles = roleIds.stream()
                .map(roleId -> SysUserRole.builder()
                        .userId(dto.getUserId())
                        .roleId(roleId)
                        .build())
                .toList();
        sysUserRoleMapper.insert(userRoles);
    }


    @Override
    public List<Long> listRoleIdsByUser(Long userId) {
        BizAssert.notNull(userId, "用户ID不能为空");
        return sysUserRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }


    @Override
    public void delete(Long id) {
        BizAssert.notNull(id, "ID 不能为空");
        sysUserMapper.deleteByIdAutoFill(id);
    }


    @Override
    public void delete(Collection<Long> ids) {
        BizAssert.notEmpty(ids, "ID 集合不能为空");
        // 批量逻辑删除：空集合不触库，超量自动分片，删除人自动填充
        sysUserMapper.deleteByIdsAutoFill(ids);
    }


}
