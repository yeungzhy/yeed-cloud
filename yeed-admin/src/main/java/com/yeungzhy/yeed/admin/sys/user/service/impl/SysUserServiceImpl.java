package com.yeungzhy.yeed.admin.sys.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.admin.sys.menu.entity.SysMenu;
import com.yeungzhy.yeed.admin.sys.menu.enums.MenuTypeEnum;
import com.yeungzhy.yeed.admin.sys.menu.mapper.SysMenuMapper;
import com.yeungzhy.yeed.admin.sys.role.entity.SysRole;
import com.yeungzhy.yeed.admin.sys.role.mapper.SysRoleMapper;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserAddDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPageDTO;
import com.yeungzhy.yeed.admin.sys.user.dto.SysUserPasswordDTO;
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
import com.yeungzhy.yeed.common.core.enums.BuiltinRoleEnum;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.common.core.result.PageResult;
import com.yeungzhy.yeed.common.core.security.LoginUserHelper;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import com.yeungzhy.yeed.common.core.support.TreeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
    // 只有用户相关写操作用到密码散列，不注册成 Bean 污染容器
    private final Argon2PasswordEncoder argon2PwdEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Resource
    private SysUserSorts sysUserSorts;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysUserConvert sysUserConvert;
    @Resource
    private BlindIndexProvider phoneBlindIndex;
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
        String employeeNo = dto.getEmployeeNo();
        // 两个登录入口共用一处唯一性判定：只查各自列的话，「A 的登录名 = B 的工号」仍能写库，登录时 OR 查询会命中两行
        BizAssert.isFalse(existsAccount(username), "系统登录名已存在");
        BizAssert.isFalse(existsAccount(employeeNo), "工号已存在");

        // 未填手机/邮箱时不生成盲索引：空值算出的 bidx 会让所有未填的用户撞同一条唯一索引
        String phone = StringUtils.trimToNull(dto.getPhone());
        String phoneBidx = phone == null ? null : phoneBlindIndex.generateHex(phone);
        if (phoneBidx != null) {
            BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getPhoneBidx, phoneBidx), "手机号已存在");
        }

        String email = normalizeEmail(dto.getEmail());
        String emailBidx = email == null ? null : emailBlindIndex.generateHex(email);
        if (emailBidx != null) {
            BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getEmailBidx, emailBidx), "邮箱已存在");
        }

        SysUser sysUser = SysUser.builder()
                .username(username)
                .password(argon2PwdEncoder.encode(dto.getPassword()))
                .realName(dto.getRealName())
                .employeeNo(employeeNo)
                // phone / email 明文入库，由 FieldCryptoInterceptor 在写库前自动 AES 加密
                .phone(phone)
                .phoneBidx(phoneBidx)
                .email(email)
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

        String realName = dto.getRealName();
        if (StringUtils.isNotEmpty(realName)) {
            updateEntity.setRealName(realName);
        }

        String username = dto.getUsername();
        if (StringUtils.isNotEmpty(username) && !username.equals(sysUser.getUsername())) {
            BizAssert.isFalse(existsAccount(username), "系统登录名已存在");
            updateEntity.setUsername(username);
        }

        String employeeNo = dto.getEmployeeNo();
        if (StringUtils.isNotEmpty(employeeNo) && !employeeNo.equals(sysUser.getEmployeeNo())) {
            BizAssert.isFalse(existsAccount(employeeNo), "工号已存在");
            updateEntity.setEmployeeNo(employeeNo);
        }

        String phone = StringUtils.trimToNull(dto.getPhone());
        if (phone != null) {
            // 加密列无法按密文比等值，判重只能走盲索引；与自身旧值相同则视为未改动
            String phoneBidx = phoneBlindIndex.generateHex(phone);
            if (!phoneBidx.equals(sysUser.getPhoneBidx())) {
                BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getPhoneBidx, phoneBidx), "手机号已存在");
            }
            updateEntity.setPhone(phone);
            updateEntity.setPhoneBidx(phoneBidx);
        }

        String email = normalizeEmail(dto.getEmail());
        if (email != null) {
            String emailBidx = emailBlindIndex.generateHex(email);
            if (!emailBidx.equals(sysUser.getEmailBidx())) {
                BizAssert.isFalse(sysUserMapper.existsByColumn(SysUser::getEmailBidx, emailBidx), "邮箱已存在");
            }
            updateEntity.setEmail(email);
            updateEntity.setEmailBidx(emailBidx);
        }

        sysUserMapper.updateById(updateEntity);
    }


    @Override
    public void changePassword(SysUserPasswordDTO dto) {
        Long currentUserId = LoginUserHelper.getUserId();
        Long targetId = dto.getId() == null ? currentUserId : dto.getId();

        SysUser sysUser = sysUserMapper.selectById(targetId);
        BizAssert.notNull(sysUser, "用户不存在");

        // 改自己的密码必须验旧：否则拿到会话（如终端未锁屏）即可直接改密接管账号
        if (targetId.equals(currentUserId)) {
            BizAssert.notBlank(dto.getOldPassword(), "原密码不能为空");
            BizAssert.isTrue(argon2PwdEncoder.matches(dto.getOldPassword(), sysUser.getPassword()), "原密码不正确");
        }

        String newPassword = dto.getNewPassword();
        BizAssert.isFalse(argon2PwdEncoder.matches(newPassword, sysUser.getPassword()), "新密码不能与原密码相同");

        // 只带 id + password，version 为 null 不触发乐观锁；审计字段由自动填充刷新（改密属资料变更）
        sysUserMapper.updateById(SysUser.builder()
                .id(targetId)
                .password(argon2PwdEncoder.encode(newPassword))
                .build());
    }


    /**
     * 账号是否已被占用（登录名与工号共用同一个登录入口，必须跨两列判定）
     */
    private boolean existsAccount(String account) {
        return sysUserMapper.existsByCondition(w -> w.eq(SysUser::getUsername, account)
                .or()
                .eq(SysUser::getEmployeeNo, account));
    }


    /**
     * 邮箱归一化：大小写与首尾空白不改变邮箱身份，必须先归一再生成盲索引
     * <p>否则 {@code A@b.com} 与 {@code a@b.com} 会算出两个不同 bidx，唯一索引形同虚设
     */
    private String normalizeEmail(String email) {
        String trimmed = StringUtils.trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }


    @Override
    public SysUserVO detail(Long id) {
        SysUser entity = sysUserMapper.selectById(id);
        BizAssert.notNull(entity, "记录不存在");
        return sysUserConvert.toVO(entity);
    }


    @Override
    public PageResult<SysUserVO> page(SysUserPageDTO dto) {
        return sysUserMapper.selectPageResult(dto, buildQueryWrapper(dto, true), sysUserConvert::toVO);
    }

    @Override
    public Long count(SysUserPageDTO dto) {
        // 计数不加投影：sqlSelect 会被拼进 COUNT(...) 变成多列 COUNT（MySQL 直接语法报错），
        // ORDER BY 同样与计数无关，带上只是给优化器添一份可能被它消不掉的无谓排序
        return sysUserMapper.selectCount(buildQueryWrapper(dto, false));
    }

    @Override
    public List<SysUserVO> slice(SysUserPageDTO dto) {
        // 不做 count：调用方（导出）已在循环外取得总数，逐页再查一遍纯属浪费
        return sysUserMapper.selectPageRecords(dto, buildQueryWrapper(dto, true), sysUserConvert::toVO);
    }

    /**
     * 构造用户查询条件（分页列表、计数、按页取数三处共用）
     *
     * <p>三处共用一份条件是刻意的：计数与取数一旦各写一套，任一侧加条件都会让「总数」与
     * 「实际取到的行」对不上，导出会表现为进度分母漂移、尾页多取或少取
     *
     * @param dto            查询条件（username / phone / email / status / 创建时间区间）
     * @param withProjection 是否带取数投影（select 列 + 排序）；计数传 false，
     *                       {@code selectCount} 会拿 sqlSelect 拼 {@code COUNT(列1,列2,...)}，多列即语法错误
     * @return 查询条件；分页字段（pageNum / pageSize）不在此处使用
     */
    private LambdaQueryWrapper<SysUser> buildQueryWrapper(SysUserPageDTO dto, boolean withProjection) {
        LambdaQueryWrapper<SysUser> lambdaQuery = Wrappers.<SysUser>lambdaQuery()
                .like(StringUtils.isNotEmpty(dto.getUsername()), SysUser::getUsername, dto.getUsername())
                .like(StringUtils.isNotEmpty(dto.getRealName()), SysUser::getRealName, dto.getRealName())
                .like(StringUtils.isNotEmpty(dto.getEmployeeNo()), SysUser::getEmployeeNo, dto.getEmployeeNo())
                .eq(Objects.nonNull(dto.getStatus()), SysUser::getStatus, dto.getStatus())
                .between(dto.hasCreateTimeRange(), SysUser::getCreateTime, dto.getCreateTimeStart(), dto.getCreateTimeEnd());
        if (withProjection) {
            lambdaQuery.select(SysUser::getId, SysUser::getUsername, SysUser::getRealName, SysUser::getEmployeeNo,
                    SysUser::getPhone, SysUser::getEmail, SysUser::getStatus,
                    SysUser::getCreateTime, SysUser::getLastLoginTime);
        }
        /*
         * 手机 / 邮箱是加密列，等值查询只能用明文生成盲索引后比 bidx
         * 注意：不能写成 .eq(condition, column, generateHex(...))
         * 实参先于 condition 求值，空值会触发 BlindIndexProvider 的 null 校验；该开关只短路 SQL 拼接，
         * 对带副作用或会抛异常的实参必须用 if 守卫
         */
        String phone = StringUtils.trimToNull(dto.getPhone());
        if (phone != null) {
            lambdaQuery.eq(SysUser::getPhoneBidx, phoneBlindIndex.generateHex(phone));
        }
        String email = normalizeEmail(dto.getEmail());
        if (email != null) {
            lambdaQuery.eq(SysUser::getEmailBidx, emailBlindIndex.generateHex(email));
        }

        if (withProjection) {
            // 应用排序：先单字段 → 再多字段（顺序敏感）；默认降序；白名单外字段静默忽略
            sysUserSorts.applyAll(lambdaQuery, dto.getOrderField(), dto.getIsAsc(), dto.getOrders());
        }
        return lambdaQuery;
    }


    /**
     * 记录最后登录时间
     *
     * <p>用 Wrapper 更新而非 {@code updateById}：后者带乐观锁 version，同一用户并发登录会互相覆盖失败，
     * 登录也不该刷新 updateBy / updateTime，审计字段要留痕的是「谁改了资料」，不是「谁登录了」
     */
    private void updateLastLoginTime(Long userId) {
        sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .set(SysUser::getLastLoginTime, LocalDateTime.now())
                .eq(SysUser::getId, userId));
    }


    @Override
    public LoginUserInfo verify(UserVerifyDTO dto) {
        BizAssert.notBlank(dto.getAccount(), "账号不能为空");
        BizAssert.notBlank(dto.getPassword(), "密码不能为空");

        /*
         * 不用 selectOne：登录名与工号是两个登录入口、共用同一命名空间，撞号时会命中两行，
         * selectOne 抛 TooManyResultsException 会变成一次 500，这里显式判定并给可读提示
         */
        List<SysUser> users = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, EnableStatusEnum.ENABLED)
                .and(w ->
                        w.eq(SysUser::getUsername, dto.getAccount())
                         .or()
                         .eq(SysUser::getEmployeeNo, dto.getAccount())));

        BizAssert.notEmpty(users, "账号不存在或已被禁用");
        BizAssert.isTrue(users.size() == 1, "账号存在歧义，请联系管理员处理");
        SysUser user = users.get(0);

        BizAssert.isTrue(argon2PwdEncoder.matches(dto.getPassword(), user.getPassword()), "密码错误");
        updateLastLoginTime(user.getId());

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
