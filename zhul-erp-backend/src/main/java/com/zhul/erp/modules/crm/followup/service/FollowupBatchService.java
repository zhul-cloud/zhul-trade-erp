package com.zhul.erp.modules.crm.followup.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.BatchFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.BatchResultVO;
import com.zhul.erp.modules.crm.followup.dto.BatchSnoozeRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.support.CustomerRollup;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 批量跟进、批量延后（spec「批量跟进与批量延后」）：逐个客户调用 {@link FollowupService}，每个客户各自一个事务，
 * 一个客户失败不影响其他客户；返回成功数和跳过 / 失败的客户及原因。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupBatchService {

    private final FollowupService followupService;
    private final FollowupItemMapper itemMapper;
    private final CustomerMapper customerMapper;

    public BatchResultVO record(BatchFollowupRequest req) {
        BatchResultVO vo = new BatchResultVO();
        Map<Long, String> names = customerNames(req.getEntries().stream().map(BatchFollowupRequest.Entry::getCustomerId).toList());
        int success = 0;
        int items = 0;
        for (BatchFollowupRequest.Entry e : req.getEntries()) {
            List<FollowupItemDO> rows = itemMapper.selectBatchIds(e.getItemIds());
            String bad = ownerReason(e, rows);
            if (bad != null) {
                fail(vo, e.getCustomerId(), names, bad);
                continue;
            }
            // 今日待办里一个客户可能在几个板块都有到期单据：每个板块各记一条
            Map<Integer, List<FollowupItemDO>> byBoard = rows.stream()
                    .collect(Collectors.groupingBy(FollowupItemDO::getBoard, TreeMap::new, Collectors.toList()));
            boolean done = false;
            for (Map.Entry<Integer, List<FollowupItemDO>> b : byBoard.entrySet()) {
                String skip = skipReason(b.getValue());
                if (skip != null) {
                    fail(vo, e.getCustomerId(), names, skip);
                    continue;
                }
                List<Long> ids = b.getValue().stream().map(FollowupItemDO::getId).toList();
                RecordFollowupRequest r = new RecordFollowupRequest();
                r.setCustomerId(e.getCustomerId());
                r.setItemIds(ids);
                // 客户情况只在商机成交生效
                r.setSituation(b.getKey() == FollowupConstants.BOARD_SALES ? req.getSituation() : null);
                r.setNextAction(req.getNextAction());
                r.setNextActionDate(req.getNextActionDate());
                r.setChannel(req.getChannel());
                r.setSummary(req.getNote());
                String key = req.getIdempotencyKey().trim() + "-" + e.getCustomerId();
                r.setIdempotencyKey(byBoard.size() > 1 ? key + "-" + b.getKey() : key);
                try {
                    followupService.record(r);
                    done = true;
                    items += ids.size();
                } catch (BizException ex) {
                    fail(vo, e.getCustomerId(), names, ex.getMessage());
                }
            }
            if (done) {
                success++;
            }
        }
        vo.setSuccess(success);
        vo.setItems(items);
        log.info("批量跟进，客户 {} 个，成功 {} 个", req.getEntries().size(), success);
        return vo;
    }

    public BatchResultVO snooze(BatchSnoozeRequest req) {
        BatchResultVO vo = new BatchResultVO();
        Map<Long, String> names = customerNames(req.getEntries().stream().map(BatchFollowupRequest.Entry::getCustomerId).toList());
        int success = 0;
        int items = 0;
        for (BatchFollowupRequest.Entry e : req.getEntries()) {
            try {
                items += followupService.snoozeMany(e.getCustomerId(), e.getItemIds(), req.getDate());
                success++;
            } catch (BizException ex) {
                fail(vo, e.getCustomerId(), names, ex.getMessage());
            }
        }
        vo.setSuccess(success);
        vo.setItems(items);
        return vo;
    }

    /** 单据已结束或不属于这个客户：整个客户都不处理 */
    private static String ownerReason(BatchFollowupRequest.Entry e, List<FollowupItemDO> rows) {
        if (rows.size() < e.getItemIds().stream().distinct().count()) {
            return "有单据已结束，请刷新后重试";
        }
        for (FollowupItemDO i : rows) {
            if (!Objects.equals(i.getCustomerId(), e.getCustomerId())) {
                return "单据不属于这个客户，请刷新后重试";
            }
        }
        return null;
    }

    /** 主动开发、客户经营和要回答阶段问题的单据（A1、A2、A5、A6）不能批量跟进 */
    private String skipReason(List<FollowupItemDO> rows) {
        for (FollowupItemDO i : rows) {
            if (!CustomerRollup.GROUPED_BOARDS.contains(i.getBoard())) {
                return FollowupConstants.BOARD_NAMES.get(i.getBoard() - 1) + "要单独记跟进";
            }
            if (CustomerRollup.ANSWER_STAGES.contains(i.getStageCode())) {
                return i.getDocNo() + " 在 " + followupService.stageLabel(i.getStageCode()) + "，要回答阶段问题，请单独记跟进";
            }
        }
        return null;
    }

    private Map<Long, String> customerNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>(4);
        }
        return customerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CustomerDO::getId, c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));
    }

    private static void fail(BatchResultVO vo, Long customerId, Map<Long, String> names, String reason) {
        BatchResultVO.Failure f = new BatchResultVO.Failure();
        f.setCustomerId(customerId);
        f.setCustomerName(names.getOrDefault(customerId, ""));
        f.setReason(reason);
        vo.getFailures().add(f);
    }
}
