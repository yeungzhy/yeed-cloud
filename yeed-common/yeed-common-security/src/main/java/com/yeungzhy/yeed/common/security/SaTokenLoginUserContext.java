package com.yeungzhy.yeed.common.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.security.LoginUserContext;
import com.yeungzhy.yeed.common.core.security.LoginUserVO;
import com.yeungzhy.yeed.common.core.security.SessionKeys;

// common-security: com.yeungzhy.yeed.common.security.SaTokenLoginUserContext
public class SaTokenLoginUserContext implements LoginUserContext {

    private final ObjectMapper objectMapper;

    public SaTokenLoginUserContext(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public LoginUserVO getLoginUser() {
        try {
            // getSession(false)：不自动创建 session，未登录时返回 null
            SaSession session = StpUtil.getSession(false);
            if (session == null) {
                return null;
            }
            Object raw = session.get(SessionKeys.LOGIN_USER);
            if (raw == null) {
                return null;
            }
            // 正常情况：sa-token 反序列化后即为 LoginUserVO
            if (raw instanceof LoginUserVO) {
                return (LoginUserVO) raw;
            }
            // 兜底：泛型擦除导致反序列化为 LinkedHashMap 时，用 Jackson 转回
            return objectMapper.convertValue(raw, LoginUserVO.class);
        } catch (Exception e) {
            // 未登录 / token 过期 / 无 sa-token 上下文（如定时任务线程）
            return null;
        }
    }
}