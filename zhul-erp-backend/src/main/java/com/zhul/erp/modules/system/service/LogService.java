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
    ForceLogoutResultVO forceLogout(List<String> tokenIds, HttpServletRequest request);
    Workbook exportLoginLogs(LoginLogQuery query);

    /**
     * 记录一条登录日志（登录成功/失败均可），使用独立事务，
     * 确保登录失败时日志不会随认证失败的外层事务一起回滚。
     */
    void recordLoginLog(Integer tenantId, Integer accountId, String username, Integer result,
                         String ip, String userAgent, String tokenId, String failReason);
}
