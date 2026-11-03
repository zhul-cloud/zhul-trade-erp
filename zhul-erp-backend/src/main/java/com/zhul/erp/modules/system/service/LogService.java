package com.zhul.erp.modules.system.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.system.dto.ForceLogoutResultVO;
import com.zhul.erp.modules.system.dto.LoginLogQuery;
import com.zhul.erp.modules.system.dto.LoginLogVO;
import com.zhul.erp.modules.system.dto.OperateLogDetailVO;
import com.zhul.erp.modules.system.dto.OperateLogQuery;
import com.zhul.erp.modules.system.dto.OperateLogVO;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

public interface LogService {
    PageResult<OperateLogVO> listOperateLogs(OperateLogQuery query);
    List<String> listOperateModules();
    OperateLogDetailVO getOperateLogDetail(Long id);
    Workbook exportOperateLogs(OperateLogQuery query);

    PageResult<LoginLogVO> listLoginLogs(LoginLogQuery query);
    /** 按会话记录 ID 强制下线，只能下线当前租户的会话 */
    ForceLogoutResultVO forceLogout(List<Integer> sessionIds, HttpServletRequest request);
    Workbook exportLoginLogs(LoginLogQuery query);

    /**
     * 记录一条登录日志（登录成功/失败均可），使用独立事务，
     * 确保登录失败时日志不会随认证失败的外层事务一起回滚。
     * sessionId 是会话记录（account_access_token）的 ID，日志里不存 Token 本身。
     */
    void recordLoginLog(Integer tenantId, Integer accountId, String username, Integer result,
                         String ip, String userAgent, Integer sessionId, String failReason);

    /**
     * 记录一条操作日志，操作人取当前登录账号，IP 取当前请求（不在请求上下文中时为空）。
     *
     * @param menu      菜单 / 模块名，如「客户管理」
     * @param operation 操作名，如「转移客户」
     * @param before    变更前内容（可为 null），序列化为 JSON 存入 content.before
     * @param after     变更后内容（可为 null），序列化为 JSON 存入 content.after
     */
    void recordOperateLog(String menu, String operation, Object before, Object after);
}
