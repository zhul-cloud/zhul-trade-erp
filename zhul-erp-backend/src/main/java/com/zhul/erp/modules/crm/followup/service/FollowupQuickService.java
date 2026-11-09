package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.QuickFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.QuickResultVO;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordResultVO;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.followup.support.NextDateSuggest;
import com.zhul.erp.modules.crm.followup.support.QuickResult;
import com.zhul.erp.modules.crm.service.OpportunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 快速跟进（spec「快速跟进」，design D4）：选一个结果，系统按阶段填好下一步和日期，写一条带「快速记录」标记的跟进。
 * 仍然走 {@link FollowupService#record}，阶段推进、单据状态、幂等与普通记跟进一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupQuickService {

    private final FollowupService followupService;
    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final OpportunityService opportunityService;
    private final FollowupSync sync;

    /** 一个客户的快速跟进；跨板块的单据各记一条。返回涉及的单据数 */
    @Transactional(rollbackFor = Exception.class)
    public int quick(QuickFollowupRequest req) {
        QuickResult result = parse(req.getResult());
        List<Long> ids = req.getItemIds().stream().filter(Objects::nonNull).distinct().toList();
        List<FollowupItemDO> rows = itemMapper.selectBatchIds(ids);
        if (rows.size() < ids.size()) {
            throw new BizException("有单据已结束，请刷新后重试");
        }
        for (FollowupItemDO i : rows) {
            if (i.getDeletedAt() != null || i.getClosedAt() != null || !Objects.equals(i.getCustomerId(), req.getCustomerId())) {
                throw new BizException("有单据已结束，请刷新后重试");
            }
            if (!result.stages().contains(i.getStageCode())) {
                throw new BizException(i.getDocNo() + " 在 " + followupService.stageLabel(i.getStageCode())
                        + "，不能选「" + result.label() + "」");
            }
        }
        LocalDate today = LocalDate.now();
        Map<String, String> actions = followupService.defaultActionTexts();
        Map<Integer, List<FollowupItemDO>> byBoard = rows.stream()
                .collect(Collectors.groupingBy(FollowupItemDO::getBoard, TreeMap::new, Collectors.toList()));
        boolean advanced = false;
        Long recordId = null;
        for (Map.Entry<Integer, List<FollowupItemDO>> b : byBoard.entrySet()) {
            List<FollowupItemDO> items = b.getValue();
            RecordFollowupRequest r = new RecordFollowupRequest();
            r.setCustomerId(req.getCustomerId());
            r.setItemIds(items.stream().map(FollowupItemDO::getId).toList());
            r.setQuick(true);
            r.setContactDate(req.getContactDate());
            r.setNextActionDate(nextDate(result, followupService.suggestFor(items, today), today));
            String stage = result.advanceTo() != null ? result.advanceTo() : items.get(0).getStageCode();
            r.setNextAction(actions.getOrDefault(stage, result.label()));
            r.setSummary(result.label());
            result.applyTo(r);
            String key = req.getIdempotencyKey().trim();
            r.setIdempotencyKey(byBoard.size() > 1 ? key + "-" + b.getKey() : key);
            RecordResultVO vo = followupService.record(r);
            advanced |= Boolean.TRUE.equals(vo.getOpportunityAdvanced());
            recordId = vo.getRecordId();
        }
        if (result == QuickResult.S1_REPLIED && opportunityService.autoAdvance(req.getCustomerId(),
                "S2", FollowupConstants.S1, "快速跟进：" + result.label()) != null) {
            // 新客户：跟进对象随商机同步
            sync.resync(req.getCustomerId(), today, false);
            advanced = true;
            if (recordId != null) {
                // 记录写在推进之前：补上推进后的阶段，时间线显示 S1 → S2
                recordMapper.update(null, new LambdaUpdateWrapper<CustomerFollowupDO>()
                        .eq(CustomerFollowupDO::getId, recordId)
                        .set(CustomerFollowupDO::getStageAfter, "S2"));
            }
        }
        log.info("快速跟进，customerId={}, result={}, items={}, advanced={}", req.getCustomerId(), result, rows.size(), advanced);
        return rows.size();
    }

    /** 各阶段可选的快速跟进结果：阶段 → 结果列表，按定义顺序 */
    public Map<String, List<QuickResultVO>> results() {
        Map<String, List<QuickResultVO>> out = new LinkedHashMap<>(64);
        for (QuickResult q : QuickResult.values()) {
            for (String stage : q.stages()) {
                out.computeIfAbsent(stage, s -> QuickResult.of(s).stream().map(FollowupQuickService::vo).toList());
            }
        }
        return out;
    }

    /** 推荐日期按结果修正：在谈条件改为明天；没回复最晚 3 天后 */
    static LocalDate nextDate(QuickResult result, NextDateSuggest.Suggestion s, LocalDate today) {
        LocalDate d = s == null ? today.plusDays(QuickResult.NO_REPLY_MAX_DAYS) : s.date();
        return switch (result.dateRule()) {
            case TOMORROW -> today.plusDays(1);
            case NO_REPLY -> d.isAfter(today.plusDays(QuickResult.NO_REPLY_MAX_DAYS))
                    ? today.plusDays(QuickResult.NO_REPLY_MAX_DAYS) : d;
            case NORMAL -> d;
        };
    }

    static QuickResult parse(String code) {
        return Arrays.stream(QuickResult.values()).filter(q -> q.name().equals(code == null ? "" : code.trim()))
                .findFirst().orElseThrow(() -> new BizException("跟进结果不正确，请刷新后重试"));
    }

    private static QuickResultVO vo(QuickResult q) {
        QuickResultVO vo = new QuickResultVO();
        vo.setCode(q.name());
        vo.setLabel(q.label());
        vo.setAdvanceTo(q.advanceTo());
        vo.setDateRule(q.dateRule().name());
        return vo;
    }
}
