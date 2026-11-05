package com.zhul.erp.modules.crm.followup.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.exception.AiUnavailableException;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.AccountCardVO;
import com.zhul.erp.modules.crm.followup.dto.DevelopCardVO;
import com.zhul.erp.modules.crm.followup.dto.ExtractRequest;
import com.zhul.erp.modules.crm.followup.dto.ExtractResultVO;
import com.zhul.erp.modules.crm.followup.dto.ReplyDraftRequest;
import com.zhul.erp.modules.crm.followup.dto.ReplyDraftVO;
import com.zhul.erp.modules.crm.followup.dto.SopCardVO;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.support.AccountStageRecorder;
import com.zhul.erp.modules.crm.followup.support.DevelopStageRecorder;
import com.zhul.erp.modules.crm.followup.support.OrderFollowupSync;
import com.zhul.erp.modules.crm.followup.support.OrderStageRecorder;
import com.zhul.erp.modules.crm.support.OpportunityStageRules;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerProfileService;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 客户跟进的 AI（design D11，tasks 7.3）：AI 整理跟进内容（异步任务，前端轮询结果）、AI 写一条英文回复（同步等待，最多 30 秒）。
 * 两个技能都按租户的 AI 配置调用，没配置时返回错误码 AI_NOT_CONFIGURED；结果只做建议，不改业务数据。
 * 本类不声明事务：任务创建后立即提交给编排服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupAiService {

    private static final long POLL_MILLIS = 500;
    private static final Map<String, Integer> ACCOUNT_ANSWER_OF = Map.of("new_need", FollowupConstants.ACCOUNT_NEW_NEED,
            "cross_sell", FollowupConstants.ACCOUNT_CROSS, "no_need", FollowupConstants.ACCOUNT_NO_NEED);
    /** 主动开发的触达结果建议（第四期 design D11） */
    private static final Map<String, Integer> TOUCH_ANSWER_OF = Map.of("replied", FollowupConstants.TOUCH_REPLIED,
            "buying_signal", FollowupConstants.TOUCH_SIGNAL, "refused", FollowupConstants.TOUCH_REFUSED);

    private final AiTaskService aiTaskService;
    private final FollowupService followupService;
    private final CustomerService customerService;
    private final CustomerProfileService profileService;
    private final CustomerFollowupMapper recordMapper;
    private final OpportunityStageRules stageRules;
    private final FollowupAccountService accountService;
    private final FollowupDevelopService developService;
    private final OrderFollowupSync orderSync;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- AI 整理

    public ExtractResultVO extract(ExtractRequest req) {
        CustomerDO c = customerService.requireInScope(req.getCustomerId());
        FollowupItemDO item = followupService.resolveItem(c.getId(), req.getItemId());
        Map<String, Object> input = context(c, item);
        input.put("blockers", followupService.blockerVos(item == null ? FollowupConstants.BOARD_SALES : item.getBoard()).stream()
                .map(b -> Map.of("code", b.getCode(), "name", b.getName())).toList());
        input.put("profileFields", profileService.blankFollowupFields(c.getId()).stream()
                .map(f -> Map.of("field", f, "label", FollowupConstants.PROFILE_LABELS.getOrDefault(f, f))).toList());
        String raw = req.getRawContent().strip();
        input.put("rawContent", raw.length() > FollowupConstants.RAW_MAX ? raw.substring(raw.length() - FollowupConstants.RAW_MAX) : raw);
        AiTaskVO task = submit(FollowupConstants.SKILL_EXTRACT, input);
        ExtractResultVO vo = new ExtractResultVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        return vo;
    }

    private ExtractResultVO load(Long taskId) {
        AiTaskVO task = ownTask(taskId, FollowupConstants.SKILL_EXTRACT);
        ExtractResultVO vo = new ExtractResultVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setErrorMessage(task.getErrorMessage());
        if (Objects.equals(task.getStatus(), AiTaskStatus.COMPLETED)) {
            vo.setResult(toResult(task.getOutput()));
        }
        return vo;
    }

    /** 处理器已按合法取值清洗过输出，这里换成前端用的结构：情况转位掩码、下一步换成日期、档案建议标出是否默认勾选 */
    private ExtractResultVO.Result toResult(String output) {
        JsonNode n = read(output);
        ExtractResultVO.Result r = new ExtractResultVO.Result();
        r.setSummary(n.path("summary").asText(""));
        int mask = 0;
        for (JsonNode s : n.path("situation")) {
            mask |= switch (s.asText()) {
                case "feedback" -> FollowupConstants.SITUATION_FEEDBACK;
                case "negotiating" -> FollowupConstants.SITUATION_NEGOTIATING;
                case "no_reply" -> FollowupConstants.SITUATION_NO_REPLY;
                case "new_need" -> FollowupConstants.SITUATION_NEW_NEED;
                case "cross_sell" -> FollowupConstants.SITUATION_CROSS;
                case "no_need" -> FollowupConstants.SITUATION_NO_NEED;
                default -> 0;
            };
            // 处理器只在客户经营板块输出这三个值，且只留一个
            Integer answer = ACCOUNT_ANSWER_OF.get(s.asText());
            if (answer != null && r.getAccountAnswer() == null) {
                r.setAccountAnswer(answer);
            }
            Integer touch = TOUCH_ANSWER_OF.get(s.asText());
            if (touch != null && r.getTouchAnswer() == null) {
                r.setTouchAnswer(touch);
            }
        }
        r.setSituation(mask);
        String blocker = n.path("blocker").asText("");
        r.setBlockerCode(blocker);
        r.setBlockerName(followupService.blockerVos(null).stream().filter(b -> b.getCode().equals(blocker))
                .map(SopCardVO.Blocker::getName).findFirst().orElse(""));
        LocalDate today = LocalDate.now();
        for (JsonNode a : n.path("nextActions")) {
            ExtractResultVO.NextAction x = new ExtractResultVO.NextAction();
            x.setText(a.path("text").asText(""));
            x.setDate(today.plusDays(Math.max(0, a.path("days").asInt(0))));
            r.getNextActions().add(x);
        }
        for (JsonNode s : n.path("profileSuggestions")) {
            ExtractResultVO.ProfileSuggestion x = new ExtractResultVO.ProfileSuggestion();
            x.setField(s.path("field").asText());
            x.setLabel(FollowupConstants.PROFILE_LABELS.getOrDefault(x.getField(), x.getField()));
            x.setValue(s.path("value").asText(""));
            x.setEvidence(s.path("evidence").asText(""));
            r.getProfileSuggestions().add(x);
        }
        r.setReceiptAnswer(optInt(n, "receiptAnswer"));
        r.setUseAnswer(optInt(n, "useAnswer"));
        n.path("issueTypes").forEach(x -> r.getIssueTypes().add(x.asText()));
        r.setSatisfaction(optInt(n, "satisfaction"));
        r.setReviewStatus(optInt(n, "reviewStatus"));
        r.setEtaDate(n.hasNonNull("etaDate") ? LocalDate.parse(n.path("etaDate").asText()) : null);
        r.setSignalText(n.path("signalText").asText(""));
        r.setTruncated(n.path("truncated").asBoolean(false));
        return r;
    }

    /** 查整理结果；档案建议按客户当前档案再看一遍：已经有值的不默认勾选 */
    public ExtractResultVO extractResult(Long taskId, Long customerId) {
        ExtractResultVO vo = load(taskId);
        if (vo.getResult() != null && customerId != null) {
            Set<String> blank = Set.copyOf(profileService.blankFollowupFields(customerId));
            vo.getResult().getProfileSuggestions().forEach(s -> s.setChecked(blank.contains(s.getField())));
        }
        return vo;
    }

    // ---------------------------------------------------------------- 英文回复

    public ReplyDraftVO replyDraft(ReplyDraftRequest req) {
        CustomerDO c = customerService.requireInScope(req.getCustomerId());
        FollowupItemDO item = followupService.resolveItem(c.getId(), req.getItemId());
        String intent = req.getIntent() == null ? "" : req.getIntent().trim();
        if (!intent.isEmpty() && !FollowupConstants.REPLY_INTENTS.contains(intent)) {
            throw new BizException("回复意图不正确");
        }
        Map<String, Object> input = context(c, item);
        input.put("intent", intent);
        String code = StringUtils.hasText(req.getBlockerCode()) ? req.getBlockerCode()
                : item == null ? "" : item.getBlockerCode();
        followupService.blockerVos(null).stream().filter(b -> b.getCode().equals(code)).findFirst()
                .ifPresent(b -> input.put("blocker", Map.of("name", b.getName(), "playbook", b.getPlaybook())));
        if (item != null && item.getLastFollowupId() != null && item.getLastFollowupId() > 0) {
            CustomerFollowupDO last = recordMapper.selectById(item.getLastFollowupId());
            if (last != null) {
                input.put("lastFollowup", Map.of("summary", nz(last.getAiSummary()), "nextAction", nz(last.getNextAction())));
            }
        }
        input.put("note", req.getNote() == null ? "" : req.getNote().trim());
        AiTaskVO task = submit(FollowupConstants.SKILL_REPLY, input);
        long deadline = System.currentTimeMillis() + FollowupConstants.REPLY_TIMEOUT_SECONDS * 1000L;
        while (true) {
            AiTaskVO cur = aiTaskService.getById(task.getId());
            if (Objects.equals(cur.getStatus(), AiTaskStatus.COMPLETED)) {
                JsonNode n = read(cur.getOutput());
                ReplyDraftVO vo = new ReplyDraftVO();
                vo.setReply(n.path("reply").asText(""));
                vo.setIntent(n.path("intent").asText(""));
                return vo;
            }
            if (Objects.equals(cur.getStatus(), AiTaskStatus.FAILED)) {
                log.warn("AI 写英文回复失败，taskId={}，原因：{}", cur.getId(), cur.getErrorMessage());
                throw BizException.of(FollowupConstants.ERROR_AI_FAILED, "AI 暂时没有写出来，请稍后再试");
            }
            if (System.currentTimeMillis() >= deadline) {
                throw BizException.of(FollowupConstants.ERROR_AI_TIMEOUT, "AI 超过 30 秒没有写出来，请稍后再试");
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw BizException.of(FollowupConstants.ERROR_AI_FAILED, "AI 暂时没有写出来，请稍后再试");
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 两个技能共用的上下文：客户、当前阶段与目标、正在跟进的单据 */
    private Map<String, Object> context(CustomerDO c, FollowupItemDO item) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("customer", Map.of("name", nz(c.getName()), "country", nz(c.getCountry()), "contactName", nz(c.getContactName())));
        if (item != null) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("code", item.getStageCode());
            stage.put("board", item.getBoard());
            stage.put("name", stageRules.load().nameOf(item.getStageCode()));
            try {
                SopCardVO sop = followupService.sop(item.getBoard(), item.getStageCode());
                stage.put("goal", sop.getGoal());
                if (OrderStageRecorder.handles(item) || AccountStageRecorder.handles(item) || DevelopStageRecorder.handles(item)) {
                    stage.put("name", sop.getName());
                }
            } catch (BizException e) {
                // 这个阶段还没有作战卡：不带目标
                stage.put("goal", "");
            }
            input.put("stage", stage);
            if (OrderStageRecorder.handles(item)) {
                input.put("order", orderContext(item));
            }
            if (AccountStageRecorder.handles(item)) {
                input.put("account", accountContext(accountService.card(c.getId())));
            } else if (DevelopStageRecorder.handles(item)) {
                input.put("prospect", prospectContext(developService.card(c.getId())));
            } else if (StringUtils.hasText(item.getDocNo()) && item.getTargetType() != FollowupConstants.TARGET_OPPORTUNITY) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("docNo", item.getDocNo());
                doc.put("currencyCode", nz(item.getCurrencyCode()));
                doc.put("amount", item.getAmount() == null ? "" : item.getAmount().toPlainString());
                doc.put("sentDays", item.getSentAt() == null ? "" : ChronoUnit.DAYS.between(item.getSentAt().toLocalDate(), LocalDate.now()));
                input.put("document", doc);
            }
        }
        return input;
    }

    /**
     * 主动开发的上下文（第四期 design D11）：为什么值得开发、背调关键发现、联系人职位、序列第几天和该节点的建议。
     * 还没开始触达时 sequenceDay 为 0（写首封）。
     */
    private Map<String, Object> prospectContext(DevelopCardVO card) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("prospectReason", nz(card.getProspectReason()));
        p.put("keyFinding", card.getReport() == null ? "" : nz(card.getReport().getKeyFinding()));
        p.put("contactTitle", card.getContacts() == null || card.getContacts().isEmpty() ? ""
                : nz(card.getContacts().get(0).getTitle()));
        int day = card.getCurrentDay() == null ? 0 : card.getCurrentDay();
        p.put("sequenceDay", day);
        Map<String, String> d5 = orderSync.defaultActions().getOrDefault("D5", Map.of());
        p.put("nodeHint", day > 0 ? nz(d5.getOrDefault("d" + day, d5.getOrDefault("default", ""))) : "");
        p.put("lastNode", day > 0 && card.getSequence() != null && !card.getSequence().isEmpty()
                && day == card.getSequence().get(card.getSequence().size() - 1));
        return p;
    }

    /** 客户经营的上下文（第三期 design D10）：触达理由、采购规律、常买品牌品类、最近一单、交叉销售建议 */
    private static Map<String, Object> accountContext(AccountCardVO card) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("reason", nz(card.getReason()));
        a.put("pattern", nz(card.getPatternText()));
        a.put("brands", nz(card.getPatternBrands()));
        a.put("lastOrder", Map.of("date", card.getLastOrderDate() == null ? "" : card.getLastOrderDate().toString(),
                "items", nz(card.getLastOrderItems())));
        a.put("crossSell", card.getCrossSell().stream().map(AccountCardVO.CrossSell::getCategory).toList());
        return a;
    }

    /** 订单履约、售后评价的上下文：订单号、现在的情况、预计送达和依据、签收日期、评价平台 */
    private static Map<String, Object> orderContext(FollowupItemDO item) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("soNo", nz(item.getDocNo()));
        order.put("situation", nz(item.getSituationText()));
        order.put("etaDate", item.getEtaDate() == null ? "" : item.getEtaDate().toString());
        order.put("etaNote", nz(item.getEtaNote()));
        order.put("signedDate", item.getSignedDate() == null ? "" : item.getSignedDate().toString());
        order.put("reviewPlatform", nz(item.getReviewPlatform()));
        return order;
    }

    private static Integer optInt(JsonNode n, String field) {
        return n.hasNonNull(field) && n.path(field).canConvertToInt() ? n.path(field).asInt() : null;
    }

    /** 提交任务；没配置 AI、订阅不可用都换成明确的错误码，前端据此置灰按钮 */
    private AiTaskVO submit(String skill, Map<String, Object> input) {
        String json;
        try {
            json = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new BizException("AI 输入整理失败", e);
        }
        try {
            return aiTaskService.createAndSubmit(skill, json, currentUser.resolve());
        } catch (AiNotConfiguredException | AiUnavailableException e) {
            throw BizException.of(FollowupConstants.ERROR_AI_NOT_CONFIGURED, e.getMessage());
        }
    }

    /** 只能查自己发起的这类任务 */
    private AiTaskVO ownTask(Long taskId, String skill) {
        AiTaskVO task;
        try {
            task = aiTaskService.getById(taskId);
        } catch (BizException e) {
            throw new BizException("AI 任务不存在", e);
        }
        if (!skill.equals(task.getSkillId()) || !Objects.equals(task.getRequestedBy(), currentUser.resolve())) {
            throw new BizException("AI 任务不存在");
        }
        return task;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            throw BizException.of(FollowupConstants.ERROR_AI_FAILED, "AI 返回的结果无法读取，请重试");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
