package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 解析当前登录用户的 user_basic.id。
 *
 * 现状：JWT 里只带 username（见 JwtAuthenticationFilter），SecurityContext 里
 * 也只存了 username，项目目前没有"当前登录用户数字ID"的现成上下文（不同于
 * TenantContext 有专门的 ThreadLocal）。给 JWT 加 userId claim、扩展登录态上下文
 * 属于登录/鉴权基础设施改动，超出本次询盘模块的任务范围，因此这里退而求其次：
 * 按 username 现查一次 user_basic，仅限本模块（owner_id/requested_by/"我的待处理"
 * 筛选）使用。如果后续别的模块也需要这个能力，建议把这个能力上移到
 * framework/security 并在登录时把 userId 一起放进 JWT，避免每次请求多一次查询。
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserBasicMapper userBasicMapper;

    /** 解析失败（未登录/找不到用户）时返回 null，调用方自行决定如何处理 */
    public Long resolve() {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null || "sys".equals(username)) {
            return null;
        }
        UserBasicDO user = userBasicMapper.selectOne(
                new LambdaQueryWrapper<UserBasicDO>().eq(UserBasicDO::getUsername, username));
        // user_basic.id 是 Integer（BaseEntity 遗留类型），本模块的 owner_id/assignee_id/
        // requested_by 按 PRD 字段定义统一用 BIGINT/Long，这里做一次装箱转换。
        return user != null && user.getId() != null ? Long.valueOf(user.getId()) : null;
    }
}
