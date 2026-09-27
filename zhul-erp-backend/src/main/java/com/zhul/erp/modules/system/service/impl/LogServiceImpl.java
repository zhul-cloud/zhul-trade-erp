package com.zhul.erp.modules.system.service.impl;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.utils.IpUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.ForceLogoutResultVO;
import com.zhul.erp.modules.system.dto.LoginLogQuery;
import com.zhul.erp.modules.system.dto.LoginLogVO;
import com.zhul.erp.modules.system.dto.OperateLogDetailVO;
import com.zhul.erp.modules.system.dto.OperateLogQuery;
import com.zhul.erp.modules.system.dto.OperateLogVO;
import com.zhul.erp.modules.system.entity.AccountAccessTokenDO;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.SysLogDO;
import com.zhul.erp.modules.system.repository.AccountAccessTokenMapper;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.SysLogMapper;
import com.zhul.erp.modules.system.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private static final int TYPE_LOGIN = 1;
    private static final int TYPE_OPERATE = 2;
    private static final int EXPORT_MAX_ROWS = 50000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysLogMapper sysLogMapper;
    private final AccountMapper accountMapper;
    private final AccountAccessTokenMapper accountAccessTokenMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    // ==================== 操作日志 ====================

    @Override
    public PageResult<OperateLogVO> listOperateLogs(OperateLogQuery query) {
        LambdaQueryWrapper<SysLogDO> wrapper = buildOperateWrapper(query);
        Page<SysLogDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
        Page<SysLogDO> pageResult = sysLogMapper.selectPage(pageParam, wrapper);
        List<OperateLogVO> records = new ArrayList<>(pageResult.getRecords().size());
        for (SysLogDO log : pageResult.getRecords()) {
            records.add(toOperateVo(log));
        }
        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public List<String> listOperateModules() {
        return sysLogMapper.selectDistinctMenus(effectiveTenantId(), TYPE_OPERATE);
    }

    @Override
    public OperateLogDetailVO getOperateLogDetail(Long id) {
        SysLogDO log = sysLogMapper.selectById(id);
        if (log == null || log.getDeletedAt() != null
                || !effectiveTenantId().equals(log.getTenantId())
                || log.getType() != TYPE_OPERATE) {
            throw new BizException("操作日志不存在");
        }

        OperateLogDetailVO vo = new OperateLogDetailVO();
        vo.setId(log.getId());
        vo.setOperatorName(log.getOperatorName());
        vo.setMenu(log.getMenu());
        vo.setOperation(log.getOperation());
        vo.setResult(log.getResult());
        vo.setIp(log.getIp());
        vo.setOperateTime(log.getOperateTime());

        String content = log.getContent();
        if (!StringUtils.hasText(content)) {
            vo.setContentEmpty(true);
            vo.setContentBroken(false);
            return vo;
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            vo.setContentEmpty(false);
            vo.setContentBroken(false);
            vo.setBefore(node.has("before") ? node.get("before") : null);
            vo.setAfter(node.has("after") ? node.get("after") : null);
        } catch (Exception e) {
            vo.setContentEmpty(false);
            vo.setContentBroken(true);
            vo.setRawContent(content);
        }
        return vo;
    }

    @Override
    public Workbook exportOperateLogs(OperateLogQuery query) {
        LambdaQueryWrapper<SysLogDO> wrapper = buildOperateWrapper(query);
        long count = sysLogMapper.selectCount(wrapper);
        if (count > EXPORT_MAX_ROWS) {
            throw new BizException("当前数据量超过5万条，请缩小查询时间范围后再导出");
        }

        List<SysLogDO> logs = sysLogMapper.selectList(wrapper);

        Workbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("操作日志");
        String[] headers = {"操作人", "操作模块", "操作名称", "操作结果", "IP地址", "操作时间"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        int rowIdx = 1;
        for (SysLogDO log : logs) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(log.getOperatorName());
            row.createCell(1).setCellValue(log.getMenu());
            row.createCell(2).setCellValue(log.getOperation());
            row.createCell(3).setCellValue(log.getResult() != null && log.getResult() == 1 ? "成功" : "失败");
            row.createCell(4).setCellValue(log.getIp());
            row.createCell(5).setCellValue(log.getOperateTime() == null ? "" : log.getOperateTime().format(TIME_FMT));
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        return workbook;
    }

    private LambdaQueryWrapper<SysLogDO> buildOperateWrapper(OperateLogQuery query) {
        LocalDateTime startTime = query.getStartTime();
        LocalDateTime endTime = query.getEndTime();
        if (startTime == null && endTime == null) {
            LocalDate today = LocalDate.now();
            startTime = today.minusDays(6).atStartOfDay();
            endTime = LocalDateTime.of(today, LocalTime.of(23, 59, 59));
        }

        LambdaQueryWrapper<SysLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLogDO::getTenantId, effectiveTenantId());
        wrapper.eq(SysLogDO::getType, TYPE_OPERATE);
        wrapper.isNull(SysLogDO::getDeletedAt);
        if (StringUtils.hasText(query.getOperatorName())) {
            wrapper.like(SysLogDO::getOperatorName, query.getOperatorName().trim());
        }
        if (StringUtils.hasText(query.getMenu())) {
            wrapper.eq(SysLogDO::getMenu, query.getMenu());
        }
        if (StringUtils.hasText(query.getOperation())) {
            wrapper.like(SysLogDO::getOperation, query.getOperation().trim());
        }
        if (query.getResult() != null) {
            wrapper.eq(SysLogDO::getResult, query.getResult());
        }
        if (startTime != null) {
            wrapper.ge(SysLogDO::getOperateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysLogDO::getOperateTime, endTime);
        }
        wrapper.orderByDesc(SysLogDO::getOperateTime);
        return wrapper;
    }

    private OperateLogVO toOperateVo(SysLogDO log) {
        OperateLogVO vo = new OperateLogVO();
        vo.setId(log.getId());
        vo.setOperatorName(log.getOperatorName());
        vo.setMenu(log.getMenu());
        vo.setOperation(log.getOperation());
        vo.setResult(log.getResult());
        vo.setIp(log.getIp());
        vo.setOperateTime(log.getOperateTime());
        return vo;
    }

    // ==================== 登录日志 ====================

    @Override
    public PageResult<LoginLogVO> listLoginLogs(LoginLogQuery query) {
        LambdaQueryWrapper<SysLogDO> wrapper = buildLoginWrapper(query);
        Page<SysLogDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
        Page<SysLogDO> pageResult = sysLogMapper.selectPage(pageParam, wrapper);
        List<LoginLogVO> records = new ArrayList<>(pageResult.getRecords().size());
        for (SysLogDO log : pageResult.getRecords()) {
            records.add(toLoginVo(log));
        }
        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForceLogoutResultVO forceLogout(List<String> tokenIds, HttpServletRequest request) {
        String currentToken = extractCurrentToken(request);
        if (currentToken != null && tokenIds.contains(currentToken)) {
            throw new BizException("不能强制下线当前登录账号");
        }

        int success = 0;
        int fail = 0;
        for (String tokenId : tokenIds) {
            try {
                redisTemplate.delete(RedisKeyConstants.TOKEN_PREFIX + tokenId);
                // 同时失效 RefreshToken 与持久化会话记录，否则"记住我"会话仍可通过 /auth/refresh 静默续期，
                // 强制下线形同虚设
                AccountAccessTokenDO row = accountAccessTokenMapper.selectOne(
                        new LambdaQueryWrapper<AccountAccessTokenDO>()
                                .eq(AccountAccessTokenDO::getAccessToken, tokenId)
                                .isNull(AccountAccessTokenDO::getDeletedAt)
                                .last("LIMIT 1"));
                if (row != null) {
                    if (StringUtils.hasText(row.getRefreshToken())) {
                        redisTemplate.delete(RedisKeyConstants.REFRESH_TOKEN_PREFIX + row.getRefreshToken());
                    }
                    row.setStatus(0);
                    row.setDeletedAt(LocalDateTime.now());
                    accountAccessTokenMapper.updateById(row);
                }
                success++;
            } catch (Exception e) {
                fail++;
            }
        }

        if (success > 0) {
            recordForceLogoutOperateLog(success, request);
        }
        return new ForceLogoutResultVO(success, fail);
    }

    @Override
    public Workbook exportLoginLogs(LoginLogQuery query) {
        LambdaQueryWrapper<SysLogDO> wrapper = buildLoginWrapper(query);
        long count = sysLogMapper.selectCount(wrapper);
        if (count > EXPORT_MAX_ROWS) {
            throw new BizException("当前数据量超过5万条，请缩小查询时间范围后再导出");
        }

        List<SysLogDO> logs = sysLogMapper.selectList(wrapper);

        Workbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("登录日志");
        String[] headers = {"序号", "用户名", "登录IP", "登录地点", "浏览器", "操作系统", "登录结果", "登录时间"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        int rowIdx = 1;
        int seq = 1;
        for (SysLogDO log : logs) {
            LoginLogVO vo = toLoginVo(log);
            Row row = sheet.createRow(rowIdx++);
            Cell seqCell = row.createCell(0);
            seqCell.setCellValue(seq++);
            row.createCell(1).setCellValue(vo.getOperatorName());
            row.createCell(2).setCellValue(vo.getIp());
            row.createCell(3).setCellValue(vo.getLocation());
            row.createCell(4).setCellValue(vo.getBrowser());
            row.createCell(5).setCellValue(vo.getOs());
            row.createCell(6).setCellValue(vo.getResult() != null && vo.getResult() == 1 ? "成功" : "失败");
            row.createCell(7).setCellValue(vo.getOperateTime() == null ? "" : vo.getOperateTime().format(TIME_FMT));
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        return workbook;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginLog(Integer tenantId, Integer accountId, String username, Integer result,
                                String ip, String userAgent, String tokenId, String failReason) {
        SysLogDO log = new SysLogDO();
        log.setTenantId(tenantId != null ? tenantId : 0);
        log.setType(TYPE_LOGIN);
        log.setOperatorCode(accountId != null ? String.valueOf(accountId) : "");
        log.setOperatorName(username != null ? username : "");
        log.setOperateTime(LocalDateTime.now());
        log.setOperation("");
        log.setMenu("");
        log.setResult(result);
        log.setIp(ip == null ? "" : ip);

        String browser = "未知";
        String os = "未知";
        try {
            UserAgent ua = UserAgentUtil.parse(userAgent);
            if (ua != null) {
                if (ua.getBrowser() != null && !ua.getBrowser().isUnknown()) {
                    browser = ua.getBrowser().toString() + " " + ua.getVersion();
                }
                if (ua.getOs() != null && !ua.getOs().isUnknown()) {
                    os = ua.getOs().toString();
                }
            }
        } catch (Exception ignored) {
            // User-Agent 解析失败，保留默认“未知”
        }

        boolean success = result != null && result == 1;
        Map<String, Object> content = new HashMap<>();
        content.put("browser", browser);
        content.put("os", os);
        content.put("location", "未知");
        content.put("token_id", success ? tokenId : null);
        content.put("is_online", success);
        content.put("fail_reason", success ? null : failReason);
        try {
            log.setContent(objectMapper.writeValueAsString(content));
        } catch (Exception e) {
            log.setContent(null);
        }
        sysLogMapper.insert(log);
    }

    private void recordForceLogoutOperateLog(int successCount, HttpServletRequest request) {
        Map<String, Object> after = new HashMap<>();
        after.put("forceLogoutCount", successCount);
        writeOperateLog("登录日志", "强制下线", null, after, request);
    }

    @Override
    public void recordOperateLog(String menu, String operation, Object before, Object after) {
        HttpServletRequest request = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            request = attrs.getRequest();
        }
        writeOperateLog(menu, operation, before, after, request);
    }

    private void writeOperateLog(String menu, String operation, Object before, Object after,
                                 HttpServletRequest request) {
        AccountDO operator = currentAccount();
        SysLogDO log = new SysLogDO();
        log.setTenantId(effectiveTenantId());
        log.setType(TYPE_OPERATE);
        log.setOperatorCode(operator != null ? String.valueOf(operator.getId()) : "");
        log.setOperatorName(operator != null ? operator.getUsername() : "");
        log.setOperateTime(LocalDateTime.now());
        log.setOperation(operation);
        log.setMenu(menu);
        log.setResult(1);
        log.setIp(request != null ? IpUtils.getClientIp(request) : "");

        Map<String, Object> content = new HashMap<>();
        content.put("before", before);
        content.put("after", after);
        try {
            log.setContent(objectMapper.writeValueAsString(content));
        } catch (Exception e) {
            log.setContent(null);
        }
        sysLogMapper.insert(log);
    }

    private LambdaQueryWrapper<SysLogDO> buildLoginWrapper(LoginLogQuery query) {
        LocalDateTime startTime = query.getStartTime();
        LocalDateTime endTime = query.getEndTime();
        if (startTime == null && endTime == null) {
            LocalDate today = LocalDate.now();
            startTime = today.minusDays(6).atStartOfDay();
            endTime = LocalDateTime.of(today, LocalTime.of(23, 59, 59));
        }

        LambdaQueryWrapper<SysLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLogDO::getTenantId, effectiveTenantId());
        wrapper.eq(SysLogDO::getType, TYPE_LOGIN);
        wrapper.isNull(SysLogDO::getDeletedAt);
        if (StringUtils.hasText(query.getOperatorName())) {
            wrapper.like(SysLogDO::getOperatorName, query.getOperatorName().trim());
        }
        if (StringUtils.hasText(query.getIp())) {
            wrapper.likeRight(SysLogDO::getIp, query.getIp().trim());
        }
        if (query.getResult() != null) {
            wrapper.eq(SysLogDO::getResult, query.getResult());
        }
        if (startTime != null) {
            wrapper.ge(SysLogDO::getOperateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysLogDO::getOperateTime, endTime);
        }
        wrapper.orderByDesc(SysLogDO::getOperateTime);
        return wrapper;
    }

    private LoginLogVO toLoginVo(SysLogDO log) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(log.getId());
        vo.setOperatorName(log.getOperatorName());
        vo.setIp(log.getIp());
        vo.setResult(log.getResult());
        vo.setOperateTime(log.getOperateTime());

        String location = "未知";
        String browser = "未知";
        String os = "未知";
        String tokenId = null;

        String content = log.getContent();
        if (StringUtils.hasText(content)) {
            try {
                JsonNode node = objectMapper.readTree(content);
                location = textOrDefault(node, "location", "未知");
                browser = textOrDefault(node, "browser", "未知");
                os = textOrDefault(node, "os", "未知");
                if (node.has("token_id") && !node.get("token_id").isNull()) {
                    tokenId = node.get("token_id").asText();
                }
            } catch (Exception ignored) {
                // content 损坏，展示字段保持“未知”
            }
        }
        vo.setLocation(location);
        vo.setBrowser(browser);
        vo.setOs(os);

        boolean online = log.getResult() != null && log.getResult() == 1
                && tokenId != null && isOnline(tokenId);
        vo.setOnline(online);
        vo.setCheckable(online);
        vo.setTokenId(online ? tokenId : null);
        return vo;
    }

    private String textOrDefault(JsonNode node, String field, String def) {
        if (node.has(field) && !node.get(field).isNull()) {
            String v = node.get(field).asText();
            return StringUtils.hasText(v) ? v : def;
        }
        return def;
    }

    private boolean isOnline(String tokenId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeyConstants.TOKEN_PREFIX + tokenId));
        } catch (Exception e) {
            return false;
        }
    }

    private AccountDO currentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return accountMapper.selectOne(new LambdaQueryWrapper<AccountDO>()
                .eq(AccountDO::getUsername, auth.getName()).last("LIMIT 1"));
    }

    private String extractCurrentToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Integer effectiveTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }
}
