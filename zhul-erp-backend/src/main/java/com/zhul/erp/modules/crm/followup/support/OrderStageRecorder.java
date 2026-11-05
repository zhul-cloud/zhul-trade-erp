package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.sales.service.SalesOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 订单履约、售后评价阶段的「记一次跟进」（第二期 design D8，spec「履约和售后阶段的记一次跟进」）：
 * 校验阶段字段 → 写进跟进记录 → 更新跟进对象 → A1 已收到无问题时确认订单客户已收货 → 重新推导阶段。
 * 在 {@code FollowupService.record} 的事务内执行。「有问题」不在这里建售后单，由前端随后打开新建售后单。
 */
@Component
@RequiredArgsConstructor
public class OrderStageRecorder {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final int NOTICE_ALL = FollowupConstants.NOTICE_WAYBILL | FollowupConstants.NOTICE_NEW_DATE
            | FollowupConstants.NOTICE_NEED_CUSTOMER | FollowupConstants.NOTICE_NO_REPLY;

    private final SalesOrderService salesOrderService;
    private final OrderFollowupSync orderSync;

    public static boolean handles(FollowupItemDO item) {
        return item != null && (Objects.equals(item.getBoard(), FollowupConstants.BOARD_FULFIL)
                || Objects.equals(item.getBoard(), FollowupConstants.BOARD_AFTER_SALES));
    }

    /** 阶段字段必须与跟进对象所在阶段匹配；各阶段的必填与取值校验 */
    public static void validate(RecordFollowupRequest req, FollowupItemDO item, LocalDate today) {
        Set<String> present = present(req);
        String stage = handles(item) ? item.getStageCode() : "";
        Set<String> allowed = allowed(stage);
        for (String f : present) {
            if (!allowed.contains(f)) {
                throw BizException.of(FollowupConstants.ERROR_STAGE_MISMATCH, "跟进对象已到其他阶段，请刷新后重试");
            }
        }
        if (req.getNoticeMask() != null && (req.getNoticeMask() & ~NOTICE_ALL) != 0) {
            throw new BizException("沟通内容不正确");
        }
        if (req.getIssueTypes() != null && !FollowupConstants.ISSUE_TYPES.containsAll(req.getIssueTypes())) {
            throw new BizException("问题类型不正确");
        }
        switch (stage) {
            case "A1" -> validateReceipt(req, today);
            case "A2" -> validateUse(req);
            case "A5" -> validateSatisfaction(req);
            case "A6" -> {
                Integer s = req.getReviewStatus();
                if (s != null && s != FollowupConstants.REVIEW_DONE && s != FollowupConstants.REVIEW_DECLINED
                        && s != FollowupConstants.REVIEW_NOT_YET) {
                    throw new BizException("评价状态不正确");
                }
            }
            default -> {
                // 订单履约：预计送达只校验非空（已出运才允许，由 allowed 控制）
            }
        }
    }

    private static void validateReceipt(RecordFollowupRequest req, LocalDate today) {
        Integer a = req.getReceiptAnswer();
        if (a == null) {
            if (req.getEtaDate() != null || req.getIssueTypes() != null) {
                throw new BizException("请选择客户收到了吗");
            }
            return;
        }
        if (a < FollowupConstants.RECEIPT_OK || a > FollowupConstants.RECEIPT_NOT_YET) {
            throw new BizException("客户收到了吗的答案不正确");
        }
        if (a == FollowupConstants.RECEIPT_ISSUE && (req.getIssueTypes() == null || req.getIssueTypes().isEmpty())) {
            throw new BizException("请选择问题类型");
        }
        if (a != FollowupConstants.RECEIPT_ISSUE && req.getIssueTypes() != null && !req.getIssueTypes().isEmpty()) {
            throw new BizException("没有问题时不用选问题类型");
        }
        if (a == FollowupConstants.RECEIPT_NOT_YET) {
            if (req.getEtaDate() == null) {
                throw new BizException("还没收到时请填新的预计送达日");
            }
            if (req.getEtaDate().isBefore(today)) {
                throw new BizException("新的预计送达日不能早于今天");
            }
        } else if (req.getEtaDate() != null) {
            throw new BizException("已收到时不用填预计送达日");
        }
    }

    private static void validateUse(RecordFollowupRequest req) {
        Integer a = req.getUseAnswer();
        if (a == null) {
            if (req.getIssueTypes() != null) {
                throw new BizException("请选择用得好吗");
            }
            return;
        }
        if (a < FollowupConstants.USE_OK || a > FollowupConstants.USE_NOT_YET) {
            throw new BizException("用得好吗的答案不正确");
        }
        if (a == FollowupConstants.USE_ISSUE && (req.getIssueTypes() == null || req.getIssueTypes().isEmpty())) {
            throw new BizException("请选择问题类型");
        }
    }

    private static void validateSatisfaction(RecordFollowupRequest req) {
        Integer s = req.getSatisfaction();
        if (s == null) {
            if (req.getReviewStatus() != null || req.getDissatisfiedReason() != null) {
                throw new BizException("请选择客户满意吗");
            }
            return;
        }
        if (!FollowupConstants.SATISFACTION_NAMES.containsKey(s)) {
            throw new BizException("满意度不正确");
        }
        if (s == FollowupConstants.SATISFIED) {
            Integer r = req.getReviewStatus();
            if (r == null || (r != FollowupConstants.REVIEW_SKIP && r != FollowupConstants.REVIEW_INVITED
                    && r != FollowupConstants.REVIEW_DONE && r != FollowupConstants.REVIEW_DECLINED)) {
                throw new BizException("请选择评价状态");
            }
            if (req.getDissatisfiedReason() != null && !req.getDissatisfiedReason().isBlank()) {
                throw new BizException("满意时不用选原因");
            }
        } else {
            String reason = req.getDissatisfiedReason();
            if (reason == null || !FollowupConstants.DISSATISFIED_REASONS.containsKey(reason)) {
                throw new BizException("请选择" + FollowupConstants.SATISFACTION_NAMES.get(s) + "的原因");
            }
            if (req.getReviewStatus() != null) {
                throw new BizException("一般或不满意时不邀请评价");
            }
        }
    }

    /** 请求里带了哪些阶段字段 */
    private static Set<String> present(RecordFollowupRequest req) {
        Set<String> out = new HashSet<>(8);
        if (req.getNoticeMask() != null && req.getNoticeMask() != 0) {
            out.add("notice");
        }
        if (req.getEtaDate() != null) {
            out.add("eta");
        }
        if (req.getReceiptAnswer() != null) {
            out.add("receipt");
        }
        if (req.getUseAnswer() != null) {
            out.add("use");
        }
        if (req.getIssueTypes() != null && !req.getIssueTypes().isEmpty()) {
            out.add("issue");
        }
        if (req.getSatisfaction() != null) {
            out.add("satisfaction");
        }
        if (req.getDissatisfiedReason() != null && !req.getDissatisfiedReason().isBlank()) {
            out.add("reason");
        }
        if (req.getReviewStatus() != null) {
            out.add("review");
        }
        if (req.getReviewPlatform() != null && !req.getReviewPlatform().isBlank()) {
            out.add("platform");
        }
        return out;
    }

    private static Set<String> allowed(String stage) {
        if (stage.startsWith("F")) {
            // 出运后（F6）才可以改预计送达
            return "F6".equals(stage) ? Set.of("notice", "eta") : Set.of("notice");
        }
        return switch (stage) {
            case "A1" -> Set.of("receipt", "issue", "eta");
            case "A2" -> Set.of("use", "issue");
            case "A5" -> Set.of("satisfaction", "reason", "review", "platform");
            case "A6" -> Set.of("review", "platform");
            default -> Set.of();
        };
    }

    /** 阶段字段写进跟进记录 */
    public static void fill(CustomerFollowupDO r, RecordFollowupRequest req) {
        r.setNoticeMask(req.getNoticeMask() == null ? 0 : req.getNoticeMask());
        r.setEtaDate(req.getEtaDate());
        r.setReceiptAnswer(req.getReceiptAnswer() == null ? 0 : req.getReceiptAnswer());
        r.setUseAnswer(req.getUseAnswer() == null ? 0 : req.getUseAnswer());
        r.setIssueTypes(req.getIssueTypes() == null ? "" : String.join(",", req.getIssueTypes()));
        r.setSatisfaction(req.getSatisfaction() == null ? 0 : req.getSatisfaction());
        r.setDissatisfiedReason(req.getDissatisfiedReason() == null ? "" : req.getDissatisfiedReason());
        r.setReviewStatus(req.getReviewStatus() == null ? 0 : req.getReviewStatus());
        r.setReviewPlatform(req.getReviewPlatform() == null ? "" : req.getReviewPlatform().trim());
    }

    /**
     * 按答案更新跟进对象（调用方随后 updateById）：改预计送达、签收、使用正常、满意度和评价状态。
     *
     * @return 履约「客户没回复」时为 true，调用方计入连续无回复次数
     */
    public static boolean applyToItem(FollowupItemDO item, RecordFollowupRequest req, LocalDate today) {
        String stage = item.getStageCode();
        boolean noReply = req.getNoticeMask() != null && (req.getNoticeMask() & FollowupConstants.NOTICE_NO_REPLY) != 0;
        if (req.getEtaDate() != null) {
            item.setEtaDate(req.getEtaDate());
            item.setEtaSource(FollowupConstants.ETA_USER);
            item.setEtaNote("业务员改为 " + MD.format(req.getEtaDate()));
            if ("A1".equals(stage)) {
                // 还没收到：按新日期再提醒
                item.setNextActionDate(req.getEtaDate());
            }
        }
        // 「已收到，有问题」也算签收，否则售后单结案后会退回 A1
        if ("A1".equals(stage) && (Objects.equals(req.getReceiptAnswer(), FollowupConstants.RECEIPT_OK)
                || Objects.equals(req.getReceiptAnswer(), FollowupConstants.RECEIPT_ISSUE))) {
            item.setSignedDate(today);
        }
        if ("A2".equals(stage) && Objects.equals(req.getUseAnswer(), FollowupConstants.USE_OK)) {
            item.setUsedOk(1);
        }
        if ("A5".equals(stage) && req.getSatisfaction() != null) {
            item.setSatisfaction(req.getSatisfaction());
            item.setDissatisfiedReason(req.getDissatisfiedReason() == null ? "" : req.getDissatisfiedReason());
            item.setReviewStatus(req.getSatisfaction() == FollowupConstants.SATISFIED ? req.getReviewStatus()
                    : FollowupConstants.REVIEW_NONE);
            if (Objects.equals(req.getReviewStatus(), FollowupConstants.REVIEW_INVITED)) {
                item.setReviewInvitedAt(today);
                item.setReviewRemindCount(0);
            }
        }
        // A6「还没评」保持已邀请，按提醒规则继续
        if ("A6".equals(stage) && req.getReviewStatus() != null && req.getReviewStatus() != FollowupConstants.REVIEW_NOT_YET) {
            item.setReviewStatus(req.getReviewStatus());
        }
        if (req.getReviewPlatform() != null && !req.getReviewPlatform().isBlank()) {
            item.setReviewPlatform(req.getReviewPlatform().trim());
        }
        return noReply;
    }

    /**
     * 跟进对象保存后：A1 已收到无问题时确认订单客户已收货，再重新推导这位客户的订单履约、售后评价。
     *
     * @return 是否把订单确认为客户已收货；不是 A1 已收到无问题时为 null
     */
    public Boolean afterSave(FollowupItemDO item, RecordFollowupRequest req, LocalDate today) {
        Boolean completed = null;
        if ("A1".equals(item.getStageCode()) && Objects.equals(req.getReceiptAnswer(), FollowupConstants.RECEIPT_OK)) {
            completed = salesOrderService.confirmReceivedByFollowup(item.getTargetId());
        }
        orderSync.resync(item.getCustomerId(), today);
        return completed;
    }
}
