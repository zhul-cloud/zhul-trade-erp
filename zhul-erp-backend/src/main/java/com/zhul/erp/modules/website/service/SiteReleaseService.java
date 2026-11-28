package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.service.SiteTaskFlow;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.PreviewVO;
import com.zhul.erp.modules.website.dto.ReleaseVO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseItemDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseStepDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteReleaseItemMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseStepMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 一键发布与发布单（spec website-publish「一键发布与发布单」）：
 * 发起时按预览生成每个商品当时的完整站点数据（快照），任务变为「发布中」，发布单排队；由 SiteReleaseDispatcher 派发。
 * 事务边界：发起在一个事务里完成（锁站点行 → 预览 → 写发布单和明细 → 任务流转），任一步失败全部回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteReleaseService {

    private final WebsiteSiteService sites;
    private final WebsiteSiteMapper siteMapper;
    private final SitePreviewService previews;
    private final SiteTaskFlow taskFlow;
    private final WebsiteReleaseMapper releaseMapper;
    private final WebsiteReleaseItemMapper itemMapper;
    private final WebsiteReleaseStepMapper stepMapper;
    private final DocumentNumberService numbers;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public WebsiteReleaseDO create(Long siteId, List<Long> taskIds) {
        WebsiteSiteDO site = sites.get(siteId);
        if (!Objects.equals(site.getPublishMode(), WebsiteConstants.MODE_AUTO)) {
            throw new BizException("只复制的站点不能一键发布，请在发品任务里手工标记已发布");
        }
        siteMapper.lock(siteId, TenantContext.getTenantId());
        List<PreviewVO> items = previews.preview(siteId, taskIds);
        List<PreviewVO> blocked = items.stream().filter(v -> !v.isPublishable()).toList();
        if (!blocked.isEmpty()) {
            throw new BizException("有 " + blocked.size() + " 个商品不能发布：" + blocked.stream()
                    .map(v -> v.getModel() + "（" + String.join("、", v.getBlockers()) + "）").collect(Collectors.joining("；")));
        }
        WebsiteReleaseDO r = new WebsiteReleaseDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setSiteId(siteId);
        r.setReleaseNo(numbers.next(DocumentType.WP));
        r.setStatus(WebsiteConstants.RELEASE_QUEUED);
        r.setItemCount(items.size());
        r.setJobNo("");
        r.setCommitSha("");
        r.setFailedStep("");
        r.setError("");
        releaseMapper.insert(r);
        for (PreviewVO v : items) {
            WebsiteReleaseItemDO i = new WebsiteReleaseItemDO();
            i.setTenantId(r.getTenantId());
            i.setReleaseId(r.getId());
            i.setWebsiteProductId(v.getWebsiteProductId());
            i.setListingTaskId(v.getTaskId());
            i.setPrevStatus(taskFlow.begin(v.getTaskId(), r.getId()));
            i.setSnapshot(json(v.getData()));
            i.setLiveUrls(json(v.getLiveUrls()));
            itemMapper.insert(i);
        }
        log.info("发起独立站发布，releaseNo={}，siteId={}，商品数={}", r.getReleaseNo(), siteId, items.size());
        return r;
    }

    /** 部署失败的发布单重试：重新排队，从部署一步开始（用上次的提交） */
    @Transactional(rollbackFor = Exception.class)
    public WebsiteReleaseDO retry(Long releaseId) {
        WebsiteReleaseDO r = get(releaseId);
        siteMapper.lock(r.getSiteId(), TenantContext.getTenantId());
        r = get(releaseId);
        if (!Objects.equals(r.getStatus(), WebsiteConstants.RELEASE_DEPLOY_FAILED)) {
            throw new BizException("只有部署失败的发布单可以重试");
        }
        r.setStatus(WebsiteConstants.RELEASE_QUEUED);
        r.setError("");
        releaseMapper.updateById(r);
        log.info("重试独立站发布单，releaseNo={}", r.getReleaseNo());
        return r;
    }

    public WebsiteReleaseDO get(Long id) {
        WebsiteReleaseDO r = id == null ? null : releaseMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null) {
            throw new BizException("发布单不存在");
        }
        return r;
    }

    /** 站点的发布单记录（最近在前） */
    public List<WebsiteReleaseDO> list(Long siteId) {
        sites.get(siteId);
        return releaseMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseDO>()
                .eq(WebsiteReleaseDO::getSiteId, siteId).isNull(WebsiteReleaseDO::getDeletedAt)
                .orderByDesc(WebsiteReleaseDO::getId).last("LIMIT 200"));
    }

    /** 包含某个发品任务的发布单（最近在前） */
    public List<WebsiteReleaseDO> listByTask(Long taskId) {
        List<Long> ids = itemMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseItemDO>()
                        .select(WebsiteReleaseItemDO::getReleaseId).eq(WebsiteReleaseItemDO::getListingTaskId, taskId))
                .stream().map(WebsiteReleaseItemDO::getReleaseId).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return releaseMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseDO>()
                .in(WebsiteReleaseDO::getId, ids).isNull(WebsiteReleaseDO::getDeletedAt)
                .orderByDesc(WebsiteReleaseDO::getId).last("LIMIT 50"));
    }

    public List<WebsiteReleaseItemDO> items(Long releaseId) {
        return itemMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseItemDO>()
                .eq(WebsiteReleaseItemDO::getReleaseId, releaseId).orderByAsc(WebsiteReleaseItemDO::getId));
    }

    public List<WebsiteReleaseStepDO> steps(Long releaseId) {
        return stepMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseStepDO>()
                .eq(WebsiteReleaseStepDO::getReleaseId, releaseId)
                .orderByAsc(WebsiteReleaseStepDO::getAttempt).orderByAsc(WebsiteReleaseStepDO::getId));
    }

    private static final String[] STATUS_NAMES = {"", "排队中", "执行中", "已完成", "失败", "部署失败"};

    public ReleaseVO toVO(WebsiteReleaseDO r, boolean full) {
        ReleaseVO v = new ReleaseVO();
        v.setId(r.getId());
        v.setSiteId(r.getSiteId());
        v.setReleaseNo(r.getReleaseNo());
        v.setStatus(r.getStatus());
        v.setStatusName(STATUS_NAMES[r.getStatus()]);
        v.setItemCount(r.getItemCount());
        v.setCreateBy(r.getCreateBy());
        v.setCreateTime(r.getCreateTime());
        v.setStartedAt(r.getStartedAt());
        v.setEndedAt(r.getEndedAt());
        v.setCommitSha(r.getCommitSha());
        v.setFailedStep(r.getFailedStep());
        v.setError(r.getError());
        v.setWarnings(tree(r.getWarnings()));
        if (full) {
            for (WebsiteReleaseItemDO i : items(r.getId())) {
                JsonNode snap = tree(i.getSnapshot());
                v.getItems().add(new ReleaseVO.Item(i.getWebsiteProductId(), i.getListingTaskId(),
                        snap == null ? null : snap.path("brand").asText(null), snap == null ? null : snap.path("model").asText(null),
                        tree(i.getLiveUrls()), tree(i.getCheckResult())));
            }
            for (WebsiteReleaseStepDO st : steps(r.getId())) {
                v.getSteps().add(new ReleaseVO.Step(st.getStep(), st.getAttempt(), st.getStatus(), st.getStartedAt(),
                        st.getEndedAt(), st.getMessage(), st.getOutput()));
            }
        }
        return v;
    }

    private JsonNode tree(String json) {
        try {
            return json == null || json.isBlank() ? null : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
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
