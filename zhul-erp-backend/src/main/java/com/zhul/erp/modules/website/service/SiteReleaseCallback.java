package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.listing.service.SiteTaskFlow;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseItemDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseStepDO;
import com.zhul.erp.modules.website.repository.WebsiteProductMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseItemMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseStepMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 发布服务的回调（每一步开始、结束，以及任务结束）。回调没有登录上下文：先在平台范围按任务号找到发布单，
 * 再用 TenantContext.runAs 按发布单所属公司处理。事务边界：一次回调一个事务（发布单、步骤、明细、站点商品、发品任务一起提交）。
 * 只处理当前任务号的回调，过期任务号（重试前的、看门狗已结束的）的回调只记日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteReleaseCallback {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_MESSAGE = 500;
    private static final int MAX_OUTPUT = 60_000;

    private final WebsiteReleaseMapper releaseMapper;
    private final WebsiteReleaseItemMapper itemMapper;
    private final WebsiteReleaseStepMapper stepMapper;
    private final WebsiteProductMapper websiteProductMapper;
    private final SiteTaskFlow taskFlow;
    private final ListingTaskMapper taskMapper;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    /** 按任务号找到所属公司的发布单；不是发布单的返回 false（交给导入、比对处理） */
    public boolean handle(JsonNode body) {
        String jobId = body.path("jobId").asText("");
        WebsiteReleaseDO found = StringUtils.hasText(jobId) ? TenantScope.platform(() -> releaseMapper.selectOne(
                new LambdaQueryWrapper<WebsiteReleaseDO>().eq(WebsiteReleaseDO::getJobNo, jobId).last("LIMIT 1"))) : null;
        if (found == null) {
            return false;
        }
        TenantContext.runAs(found.getTenantId(), () -> tx.executeWithoutResult(s -> {
            WebsiteReleaseDO r = releaseMapper.selectById(found.getId());
            if ("step".equals(body.path("event").asText())) {
                step(r, jobId, body);
            } else if ("done".equals(body.path("event").asText())) {
                done(r, jobId, body);
            }
        }));
        return true;
    }

    private void step(WebsiteReleaseDO r, String jobId, JsonNode b) {
        String step = b.path("step").asText("");
        if (step.isEmpty() || step.length() > 16) {
            return;
        }
        int attempt = SiteReleaseDispatcher.attemptOf(jobId);
        WebsiteReleaseStepDO row = stepMapper.selectOne(new LambdaQueryWrapper<WebsiteReleaseStepDO>()
                .eq(WebsiteReleaseStepDO::getReleaseId, r.getId()).eq(WebsiteReleaseStepDO::getAttempt, attempt)
                .eq(WebsiteReleaseStepDO::getStep, step));
        boolean isNew = row == null;
        if (isNew) {
            row = new WebsiteReleaseStepDO();
            row.setTenantId(r.getTenantId());
            row.setReleaseId(r.getId());
            row.setAttempt(attempt);
            row.setStep(step);
            row.setMessage("");
        }
        row.setStatus(switch (b.path("status").asText()) {
            case "success" -> WebsiteConstants.STEP_SUCCESS;
            case "failed" -> WebsiteConstants.STEP_FAILED;
            case "warning" -> WebsiteConstants.STEP_WARNING;
            default -> WebsiteConstants.STEP_RUNNING;
        });
        LocalDateTime started = time(b.path("startedAt").asText(null));
        if (started != null) {
            row.setStartedAt(started);
        }
        row.setEndedAt(time(b.path("endedAt").asText(null)));
        if (b.hasNonNull("message")) {
            row.setMessage(cut(b.path("message").asText(), MAX_MESSAGE));
        }
        if (b.hasNonNull("output")) {
            row.setOutput(cut(b.path("output").asText(), MAX_OUTPUT));
        }
        if (isNew) {
            stepMapper.insert(row);
        } else {
            stepMapper.updateById(row);
        }
    }

    private void done(WebsiteReleaseDO r, String jobId, JsonNode b) {
        if (!Objects.equals(r.getStatus(), WebsiteConstants.RELEASE_RUNNING) || !Objects.equals(r.getJobNo(), jobId)) {
            log.warn("忽略过期的发布回调，releaseNo={}，jobId={}", r.getReleaseNo(), jobId);
            return;
        }
        String status = b.path("status").asText();
        JsonNode result = b.path("result");
        if ("success".equals(status)) {
            succeed(r, result);
        } else if ("deploy_failed".equals(status)) {
            r.setStatus(WebsiteConstants.RELEASE_DEPLOY_FAILED);
            r.setCommitSha(cut(b.path("result").path("commit").asText(b.path("commit").asText(r.getCommitSha())), 40));
            r.setFailedStep(WebsiteConstants.STEP_DEPLOY);
            r.setError(cut(b.path("error").asText("部署失败"), MAX_MESSAGE));
            r.setEndedAt(LocalDateTime.now());
            releaseMapper.updateById(r);
            // 线上可能只更新了一部分：任务保持「发布中」，等重试
            log.warn("独立站发布单部署失败，releaseNo={}", r.getReleaseNo());
        } else {
            fail(r, b.path("failedStep").asText(""), b.path("error").asText("发布失败"));
        }
    }

    private void succeed(WebsiteReleaseDO r, JsonNode result) {
        Map<String, JsonNode> checks = new HashMap<>();
        for (JsonNode c : result.path("online")) {
            checks.put(c.path("url").asText(), c);
        }
        List<String> warnings = new ArrayList<>();
        result.path("warnings").forEach(w -> warnings.add(w.asText()));
        r.setStatus(WebsiteConstants.RELEASE_DONE);
        String commit = result.path("commit").asText("");
        r.setCommitSha(commit.length() > 40 ? commit.substring(0, 40) : commit);
        r.setFailedStep("");
        r.setError("");
        r.setWarnings(json(warnings));
        r.setEndedAt(LocalDateTime.now());
        releaseMapper.updateById(r);
        for (WebsiteReleaseItemDO i : items(r.getId())) {
            Map<String, Object> urls = readMap(i.getLiveUrls());
            List<Object> itemChecks = new ArrayList<>();
            for (Object u : urls.values()) {
                JsonNode c = checks.get(String.valueOf(u));
                if (c != null) {
                    itemChecks.add(c);
                }
            }
            i.setCheckResult(json(itemChecks));
            itemMapper.updateById(i);

            Map<String, Object> snapshot = readMap(i.getSnapshot());
            snapshot.put("release", r.getReleaseNo());
            WebsiteProductDO wp = websiteProductMapper.selectById(i.getWebsiteProductId());
            if (wp != null) {
                wp.setLastSnapshot(json(snapshot));
                wp.setLastReleaseId(r.getId());
                wp.setSlugLocked(1);
                wp.setLiveUrls(i.getLiveUrls());
                websiteProductMapper.updateById(wp);
            }
            String first = urls.values().stream().map(String::valueOf).findFirst().orElse("");
            if (inRelease(i.getListingTaskId(), r.getId())) {
                taskFlow.succeed(i.getListingTaskId(), r.getId(), first, r.getCreateBy());
            }
        }
        log.info("独立站发布单完成，releaseNo={}，警告数={}", r.getReleaseNo(), warnings.size());
    }

    private void fail(WebsiteReleaseDO r, String failedStep, String error) {
        r.setStatus(WebsiteConstants.RELEASE_FAILED);
        r.setFailedStep(cut(failedStep, 16));
        r.setError(cut(error, MAX_MESSAGE));
        r.setEndedAt(LocalDateTime.now());
        releaseMapper.updateById(r);
        for (WebsiteReleaseItemDO i : items(r.getId())) {
            if (inRelease(i.getListingTaskId(), r.getId())) {
                taskFlow.fail(i.getListingTaskId(), r.getId(), i.getPrevStatus());
            }
        }
        log.warn("独立站发布单失败，releaseNo={}，步骤={}", r.getReleaseNo(), failedStep);
    }

    /** 提交发布服务失败、看门狗超时：标为失败、任务退回（需在所属公司的租户上下文里调用） */
    public void finishFailed(Long releaseId, String jobNo, String step, String error) {
        tx.executeWithoutResult(s -> {
            WebsiteReleaseDO r = releaseMapper.selectById(releaseId);
            if (r != null && Objects.equals(r.getStatus(), WebsiteConstants.RELEASE_RUNNING) && Objects.equals(r.getJobNo(), jobNo)) {
                fail(r, step, error == null ? "发布失败" : error);
            }
        });
    }

    /** 当前执行中的最后一步（看门狗写失败步骤用） */
    public String runningStep(Long releaseId, String jobNo) {
        WebsiteReleaseStepDO last = stepMapper.selectOne(new LambdaQueryWrapper<WebsiteReleaseStepDO>()
                .eq(WebsiteReleaseStepDO::getReleaseId, releaseId)
                .eq(WebsiteReleaseStepDO::getAttempt, SiteReleaseDispatcher.attemptOf(jobNo))
                .orderByDesc(WebsiteReleaseStepDO::getId).last("LIMIT 1"));
        return last == null ? "" : last.getStep();
    }

    /** 任务仍在这张发布单的「发布中」；不在的（被删除等）跳过，不让一个任务卡住整张发布单 */
    private boolean inRelease(Long taskId, Long releaseId) {
        ListingTaskDO t = taskMapper.selectById(taskId);
        boolean ok = t != null && t.getDeletedAt() == null && Objects.equals(t.getStatus(), ListingConstants.STATUS_PUBLISHING)
                && Objects.equals(t.getReleaseId(), releaseId);
        if (!ok) {
            log.warn("发品任务不在这张发布单的发布中，跳过，taskId={}，releaseId={}", taskId, releaseId);
        }
        return ok;
    }

    private List<WebsiteReleaseItemDO> items(Long releaseId) {
        return itemMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseItemDO>()
                .eq(WebsiteReleaseItemDO::getReleaseId, releaseId).orderByAsc(WebsiteReleaseItemDO::getId));
    }

    /** 发布服务的时间是 ISO（UTC），存北京时间 */
    static LocalDateTime time(String iso) {
        if (!StringUtils.hasText(iso)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("发布单数据读取失败", e);
        }
    }

    private String json(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("发布单序列化失败", e);
        }
    }
}
