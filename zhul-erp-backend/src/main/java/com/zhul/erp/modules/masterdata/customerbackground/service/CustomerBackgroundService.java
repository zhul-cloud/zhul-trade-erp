package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportVO;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundInput;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.ERR_RECENT;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.ERR_RUNNING;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.REMIND_HOURS;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.SKILL;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.SOURCE_AI;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_FAILED;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_RUNNING;

/** 客户背调（spec crm/customer-background）：发起 AI 背调 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBackgroundService {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final CustomerBackgroundReportMapper reportMapper;
    private final BackgroundInput input;
    private final AiTaskService aiTaskService;
    private final DocumentNumberService documentNumberService;
    private final UserBasicMapper userBasicMapper;
    private final CurrentUserResolver currentUser;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    /**
     * 发起 AI 背调。事务边界：锁客户行 → 检查背调中与 24 小时提醒 → 建 AI 任务与报告，一起提交；
     * 任务在事务提交后才发给编排服务（AiTaskService），AI 未配置时抛出提示、不生成报告。
     */
    @Transactional(rollbackFor = Exception.class)
    public BackgroundReportVO research(Long customerId, boolean confirmed) {
        CustomerDO c = customerService.requireInScope(customerId);
        // 同一客户串行：两个人同时点「AI 背调」时，后一个等前一个提交后再检查，看到的是「背调中」
        customerMapper.lockById(c.getId());
        if (reportMapper.selectCount(active(c.getId()).eq(CustomerBackgroundReportDO::getStatus, STATUS_RUNNING)) > 0) {
            throw BizException.of(ERR_RUNNING, "这个客户正在背调中，完成后再发起");
        }
        if (!confirmed) {
            remindIfRecent(c.getId());
        }
        Long me = currentUser.resolve();
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL, json(input.research(c)), me);

        CustomerBackgroundReportDO r = new CustomerBackgroundReportDO();
        r.setTenantId(c.getTenantId());
        r.setReportNo(documentNumberService.next(DocumentType.BG));
        r.setCustomerId(c.getId());
        r.setSource(SOURCE_AI);
        r.setStatus(STATUS_RUNNING);
        r.setAiTaskId(task.getId());
        r.setRequestedBy(me == null ? 0L : me);
        reportMapper.insert(r);
        log.info("发起客户背调，customerId={}, reportNo={}, aiTaskId={}", c.getId(), r.getReportNo(), task.getId());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("customerCode", c.getCustomerCode());
        after.put("报告编号", r.getReportNo());
        logService.recordOperateLog("客户管理", "发起 AI 背调", null, after);
        return toVo(reportMapper.selectById(r.getId()));
    }

    /** 24 小时内已有背调（背调中或已完成）时提醒，确认后才发起 */
    private void remindIfRecent(Long customerId) {
        CustomerBackgroundReportDO last = reportMapper.selectOne(active(customerId)
                .ne(CustomerBackgroundReportDO::getStatus, STATUS_FAILED)
                .ge(CustomerBackgroundReportDO::getCreateTime, LocalDateTime.now().minusHours(REMIND_HOURS))
                .orderByDesc(CustomerBackgroundReportDO::getCreateTime)
                .last("LIMIT 1"));
        if (last == null) {
            return;
        }
        long minutes = Math.max(0, Duration.between(last.getCreateTime(), LocalDateTime.now()).toMinutes());
        String ago = minutes < 60 ? "不到 1 小时前" : " " + (minutes / 60) + " 小时前";
        String who = nameOf(last.getRequestedBy());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reportNo", last.getReportNo());
        detail.put("hoursAgo", minutes / 60);
        detail.put("requestedByName", who);
        detail.put("createTime", last.getCreateTime());
        throw BizException.of(ERR_RECENT, "这个客户" + ago + "由" + who + "背调过，仍要再背调吗？", detail);
    }

    private static LambdaQueryWrapper<CustomerBackgroundReportDO> active(Long customerId) {
        return new LambdaQueryWrapper<CustomerBackgroundReportDO>()
                .eq(CustomerBackgroundReportDO::getCustomerId, customerId)
                .isNull(CustomerBackgroundReportDO::getDeletedAt);
    }

    private String nameOf(Long userId) {
        if (userId == null || userId == 0) {
            return "系统";
        }
        UserBasicDO u = userBasicMapper.selectById(userId);
        return u == null ? "已删除的用户" : u.getName();
    }

    private BackgroundReportVO toVo(CustomerBackgroundReportDO r) {
        BackgroundReportVO vo = new BackgroundReportVO();
        vo.setId(r.getId());
        vo.setReportNo(r.getReportNo());
        vo.setCustomerId(r.getCustomerId());
        vo.setSource(r.getSource());
        vo.setStatus(r.getStatus());
        vo.setRequestedBy(r.getRequestedBy());
        vo.setRequestedByName(nameOf(r.getRequestedBy()));
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("背调输入组装失败", e);
        }
    }
}
