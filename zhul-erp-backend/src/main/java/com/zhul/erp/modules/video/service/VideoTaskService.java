package com.zhul.erp.modules.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.framework.storage.VideoTranscoder;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.product.dto.ProductOptionVO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductNames;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.dto.BatchQueueVO;
import com.zhul.erp.modules.video.dto.MaterialsRequest;
import com.zhul.erp.modules.video.dto.VideoScript;
import com.zhul.erp.modules.video.dto.VideoTaskDetailVO;
import com.zhul.erp.modules.video.dto.VideoTaskQuery;
import com.zhul.erp.modules.video.dto.VideoTaskVO;
import com.zhul.erp.modules.video.entity.VideoLogDO;
import com.zhul.erp.modules.video.entity.VideoMaterialDO;
import com.zhul.erp.modules.video.entity.VideoOutputDO;
import com.zhul.erp.modules.video.entity.VideoTaskDO;
import com.zhul.erp.modules.video.repository.VideoLogMapper;
import com.zhul.erp.modules.video.repository.VideoMaterialMapper;
import com.zhul.erp.modules.video.repository.VideoOutputMapper;
import com.zhul.erp.modules.video.repository.VideoTaskMapper;
import com.zhul.erp.modules.video.support.ScriptRules;
import com.zhul.erp.modules.video.support.VideoSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 视频任务（spec product/video-task）：一家公司一个商品一个任务，上传图片、排序选用、脚本、加入队列、审核。
 * 状态流转集中在 {@link #requireStatus}，不符合流转的操作一律拒绝并提示当前状态。
 * 事务边界：每个写操作一个事务（任务、素材、附件、素材库索引同时提交）；文件先写存储再写库，库写失败时文件成为孤儿，由存储清理处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTaskService {

    private static final Map<String, List<Integer>> TABS = new LinkedHashMap<>();
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "png", "webp");
    private static final Set<String> NAME_EXTS = Set.of("jpg", "jpeg", "png", "webp");
    private static final String TYPE_MESSAGE = "只支持 JPG、PNG、WEBP 图片，请先转成 JPG";
    private static final Map<String, String> LOG_NAMES = Map.of(VideoConstants.LOG_QUEUE, "加入队列",
            VideoConstants.LOG_DEQUEUE, "移出队列", VideoConstants.LOG_START, "开始生成", VideoConstants.LOG_DONE, "生成完成",
            VideoConstants.LOG_FAIL, "生成失败", VideoConstants.LOG_APPROVE, "审核通过", VideoConstants.LOG_REJECT, "退回");
    private static final Map<Integer, String> OUTPUT_NAMES = Map.of(VideoConstants.OUTPUT_REVIEW, "待审核",
            VideoConstants.OUTPUT_APPROVED, "已通过", VideoConstants.OUTPUT_REPLACED, "已替换", VideoConstants.OUTPUT_REJECTED, "已退回");
    /** 还没有生成记录时，按每个任务 6 分钟估算预计开始时间 */
    private static final long DEFAULT_MINUTES = 6;

    static {
        TABS.put("all", null);
        TABS.put("pending", List.of(VideoConstants.STATUS_PENDING));
        TABS.put("queued", List.of(VideoConstants.STATUS_QUEUED, VideoConstants.STATUS_GENERATING));
        TABS.put("review", List.of(VideoConstants.STATUS_REVIEW));
        TABS.put("approved", List.of(VideoConstants.STATUS_APPROVED));
        TABS.put("rejected", List.of(VideoConstants.STATUS_REJECTED));
        TABS.put("failed", List.of(VideoConstants.STATUS_FAILED));
    }

    private final VideoTaskMapper taskMapper;
    private final VideoMaterialMapper materialMapper;
    private final VideoOutputMapper outputMapper;
    private final VideoLogMapper logMapper;
    private final BizAttachmentMapper attachmentMapper;
    private final ProductMapper productMapper;
    private final ProductNames productNames;
    private final ProductLookupService productLookup;
    private final FileStorageService files;
    private final MediaIndexer mediaIndexer;
    private final CurrentUserResolver currentUser;
    private final VideoSettings settings;
    private final ObjectMapper objectMapper;
    private final VideoFacts productFacts;

    // ================================================================ 列表与详情

    public PageResult<VideoTaskVO> page(VideoTaskQuery q) {
        String tab = StringUtils.hasText(q.getTab()) ? q.getTab() : "all";
        if (!TABS.containsKey(tab)) {
            throw new BizException("页签无效");
        }
        String keyword = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        String mpn = keyword == null ? null : MpnNormalizer.normalize(keyword);
        mpn = StringUtils.hasText(mpn) ? mpn : null;
        int size = q.getPageSize() == null ? 20 : Math.max(1, Math.min(100, q.getPageSize()));
        int page = q.getPage() == null ? 1 : Math.max(1, q.getPage());
        Integer tenantId = TenantContext.getTenantId();
        long total = taskMapper.count(tenantId, TABS.get(tab), q.getBrandId(), q.getCategoryId(), keyword, mpn);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        List<Long> ids = taskMapper.pageIds(tenantId, TABS.get(tab), q.getBrandId(), q.getCategoryId(), keyword, mpn,
                (long) (page - 1) * size, size);
        List<VideoTaskDO> tasks = ids.isEmpty() ? List.of() : taskMapper.selectBatchIds(ids);
        Map<Long, VideoTaskDO> byId = tasks.stream().collect(Collectors.toMap(VideoTaskDO::getId, Function.identity()));
        List<VideoTaskVO> rows = toVos(ids.stream().map(byId::get).filter(Objects::nonNull).toList(), VideoTaskVO::new);
        return PageResult.of(total, rows);
    }

    /** 各页签的任务数 */
    public Map<String, Long> counts(VideoTaskQuery q) {
        String keyword = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        String mpn = keyword == null ? null : MpnNormalizer.normalize(keyword);
        Map<Integer, Long> byStatus = new HashMap<>();
        for (Map<String, Object> r : taskMapper.statusCounts(TenantContext.getTenantId(), q.getBrandId(), q.getCategoryId(), keyword,
                StringUtils.hasText(mpn) ? mpn : null)) {
            byStatus.put(((Number) r.get("status")).intValue(), ((Number) r.get("cnt")).longValue());
        }
        Map<String, Long> out = new LinkedHashMap<>();
        TABS.forEach((tab, statuses) -> out.put(tab, statuses == null
                ? byStatus.values().stream().mapToLong(Long::longValue).sum()
                : statuses.stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum()));
        return out;
    }

    public VideoTaskDetailVO detail(Long id) {
        return detailOf(require(id));
    }

    /** 商品的视频任务（商品档案、单个上传前查看）；没有时为空 */
    public VideoTaskDetailVO byProduct(Long productId) {
        VideoTaskDO t = taskOfProduct(productId);
        return t == null ? null : detailOf(t);
    }

    /** 下一个待审核的任务（按更新时间先后），审核页切换用；没有时为空 */
    public Long nextReview(Long afterId) {
        VideoTaskDO t = taskMapper.selectOne(new LambdaQueryWrapper<VideoTaskDO>()
                .select(VideoTaskDO::getId)
                .eq(VideoTaskDO::getTenantId, TenantContext.getTenantId())
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_REVIEW)
                .ne(afterId != null, VideoTaskDO::getId, afterId)
                .isNull(VideoTaskDO::getDeletedAt)
                .orderByAsc(VideoTaskDO::getFinishedAt)
                .orderByAsc(VideoTaskDO::getId)
                .last("LIMIT 1"));
        return t == null ? null : t.getId();
    }

    private VideoTaskDetailVO detailOf(VideoTaskDO t) {
        VideoTaskDetailVO vo = toVos(List.of(t), VideoTaskDetailVO::new).get(0);
        List<VideoMaterialDO> materials = materials(t.getId());
        Map<Long, BizAttachmentDO> atts = attachments(materials.stream().map(VideoMaterialDO::getAttachmentId).toList());
        for (VideoMaterialDO m : materials) {
            BizAttachmentDO a = atts.get(m.getAttachmentId());
            VideoTaskDetailVO.Material mv = new VideoTaskDetailVO.Material();
            mv.setAttachmentId(m.getAttachmentId());
            mv.setFileName(a == null ? "" : a.getFileName());
            mv.setFileSize(a == null ? 0L : a.getFileSize());
            mv.setSort(m.getSort());
            mv.setUsed(Objects.equals(m.getUsed(), 1));
            vo.getMaterials().add(mv);
        }
        VideoScript script = readScript(t);
        vo.setScript(script);
        vo.setScriptSource(t.getScriptSource());
        vo.setScriptStatus(t.getScriptStatus());
        vo.setScriptError(t.getScriptError());
        if (script != null) {
            script.getShots().forEach(s -> vo.getShotSeconds().add(ScriptRules.seconds(s.getCaption())));
            vo.setTotalSeconds(ScriptRules.totalSeconds(script));
        }
        vo.setFactsUnverified(factsUnverified(t.getProductId()));
        List<VideoOutputDO> outputs = outputMapper.selectList(new LambdaQueryWrapper<VideoOutputDO>()
                .eq(VideoOutputDO::getTaskId, t.getId())
                .isNull(VideoOutputDO::getDeletedAt)
                .orderByAsc(VideoOutputDO::getId));
        for (VideoOutputDO o : outputs) {
            if (Objects.equals(o.getVersion(), t.getCurrentVersion())) {
                vo.getOutputs().add(outputVo(o));
            }
            if (Objects.equals(o.getStatus(), VideoConstants.OUTPUT_APPROVED)) {
                vo.getApprovedOutputs().add(outputVo(o));
            }
        }
        for (VideoLogDO l : logMapper.selectList(new LambdaQueryWrapper<VideoLogDO>()
                .eq(VideoLogDO::getTaskId, t.getId()).orderByDesc(VideoLogDO::getId))) {
            VideoTaskDetailVO.Log lv = new VideoTaskDetailVO.Log();
            lv.setAction(l.getAction());
            lv.setActionName(LOG_NAMES.getOrDefault(l.getAction(), l.getAction()));
            lv.setVersion(l.getVersion());
            lv.setNote(l.getNote());
            lv.setOperator(l.getOperator());
            lv.setTime(l.getCreateTime());
            vo.getLogs().add(lv);
        }
        return vo;
    }

    private static VideoTaskDetailVO.Output outputVo(VideoOutputDO o) {
        VideoTaskDetailVO.Output ov = new VideoTaskDetailVO.Output();
        ov.setId(o.getId());
        ov.setVersion(o.getVersion());
        ov.setAspect(o.getAspect());
        ov.setVideoAttachmentId(o.getVideoAttachmentId());
        ov.setCoverAttachmentId(o.getCoverAttachmentId());
        ov.setDurationSec(o.getDurationSec());
        ov.setStatus(o.getStatus());
        ov.setStatusName(OUTPUT_NAMES.get(o.getStatus()));
        return ov;
    }

    private <T extends VideoTaskVO> List<T> toVos(List<VideoTaskDO> tasks, java.util.function.Supplier<T> factory) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> taskIds = tasks.stream().map(VideoTaskDO::getId).toList();
        Map<Long, ProductDO> products = productMapper.selectBatchIds(tasks.stream().map(VideoTaskDO::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductDO::getId, Function.identity()));
        ProductNames.Lookup names = productNames.load(products.values());
        Map<Long, List<VideoMaterialDO>> materials = materialMapper.selectList(new LambdaQueryWrapper<VideoMaterialDO>()
                        .in(VideoMaterialDO::getTaskId, taskIds).isNull(VideoMaterialDO::getDeletedAt)).stream()
                .collect(Collectors.groupingBy(VideoMaterialDO::getTaskId));
        Map<Long, VideoOutputDO> approvedPortrait = outputMapper.selectList(new LambdaQueryWrapper<VideoOutputDO>()
                        .in(VideoOutputDO::getTaskId, taskIds)
                        .eq(VideoOutputDO::getStatus, VideoConstants.OUTPUT_APPROVED)
                        .eq(VideoOutputDO::getAspect, VideoConstants.PORTRAIT)
                        .isNull(VideoOutputDO::getDeletedAt)).stream()
                .collect(Collectors.toMap(VideoOutputDO::getTaskId, Function.identity(), (a, b) -> a.getVersion() > b.getVersion() ? a : b));
        Map<Long, Integer> positions = queuePositions();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime open = positions.isEmpty() ? now : settings.window().nextOpen(now);
        long minutes = positions.isEmpty() ? DEFAULT_MINUTES : averageMinutes();
        List<T> out = new ArrayList<>(tasks.size());
        for (VideoTaskDO t : tasks) {
            T vo = factory.get();
            vo.setId(t.getId());
            vo.setProductId(t.getProductId());
            vo.setBrand(t.getBrand());
            vo.setModel(t.getModel());
            ProductDO p = products.get(t.getProductId());
            vo.setProductName(p == null ? "" : p.getProductName());
            ProductCategoryDO c = p == null ? null : names.categories().get(p.getCategoryId());
            vo.setCategory(c == null ? "" : StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName());
            vo.setStatus(t.getStatus());
            vo.setStatusName(VideoConstants.STATUS_NAMES.get(t.getStatus()));
            Integer pos = positions.get(t.getId());
            if (pos != null && t.getStatus() == VideoConstants.STATUS_QUEUED) {
                vo.setQueuePosition(pos);
                vo.setExpectedAt(open.plusMinutes(minutes * (pos - 1)));
            }
            vo.setFailReason(t.getFailReason());
            vo.setRejectReason(t.getRejectReason());
            List<VideoMaterialDO> ms = materials.getOrDefault(t.getId(), List.of());
            vo.setImageCount(ms.size());
            vo.setUsedCount((int) ms.stream().filter(m -> Objects.equals(m.getUsed(), 1)).count());
            vo.setThumbnailIds(ms.stream().filter(m -> Objects.equals(m.getUsed(), 1))
                    .sorted(Comparator.comparing(VideoMaterialDO::getSort).thenComparing(VideoMaterialDO::getId))
                    .limit(3).map(VideoMaterialDO::getAttachmentId).toList());
            VideoOutputDO o = approvedPortrait.get(t.getId());
            if (o != null) {
                vo.setVideoAttachmentId(o.getVideoAttachmentId());
                vo.setCoverAttachmentId(o.getCoverAttachmentId());
            }
            vo.setCreateTime(t.getCreateTime());
            vo.setCreateBy(t.getCreateBy());
            vo.setUpdateTime(t.getUpdateTime());
            vo.setUpdateBy(t.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    /** 全服务器队列里的位置（按加入队列先后，从 1 起）；只返回本公司任务的位置，不暴露其他公司的任务 */
    private Map<Long, Integer> queuePositions() {
        Integer tenantId = TenantContext.getTenantId();
        List<VideoTaskDO> queue = TenantScope.platform(() -> taskMapper.selectList(new LambdaQueryWrapper<VideoTaskDO>()
                .select(VideoTaskDO::getId, VideoTaskDO::getTenantId)
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_QUEUED)
                .isNull(VideoTaskDO::getDeletedAt)
                .orderByAsc(VideoTaskDO::getQueuedAt)
                .orderByAsc(VideoTaskDO::getId)));
        Map<Long, Integer> out = new HashMap<>();
        for (int i = 0; i < queue.size(); i++) {
            if (Objects.equals(queue.get(i).getTenantId(), tenantId)) {
                out.put(queue.get(i).getId(), i + 1);
            }
        }
        return out;
    }

    /** 最近 20 次成功生成的平均耗时（分钟），没有记录时按 6 分钟 */
    private long averageMinutes() {
        List<VideoTaskDO> done = TenantScope.platform(() -> taskMapper.selectList(new LambdaQueryWrapper<VideoTaskDO>()
                .select(VideoTaskDO::getStartedAt, VideoTaskDO::getFinishedAt)
                .in(VideoTaskDO::getStatus, VideoConstants.STATUS_REVIEW, VideoConstants.STATUS_APPROVED, VideoConstants.STATUS_REJECTED)
                .isNotNull(VideoTaskDO::getStartedAt)
                .isNotNull(VideoTaskDO::getFinishedAt)
                .orderByDesc(VideoTaskDO::getFinishedAt)
                .last("LIMIT 20")));
        return Math.max(1, Math.round(done.stream()
                .mapToLong(t -> Duration.between(t.getStartedAt(), t.getFinishedAt()).toMinutes())
                .filter(m -> m >= 0).average().orElse(DEFAULT_MINUTES)));
    }

    // ================================================================ 上传与素材

    /**
     * 为一个商品上传一张图片：没有任务时创建（一个商品只有一个任务），有则追加。
     * 扩展名与文件头都要是 JPG、PNG、WEBP（HEIC 等拒绝并提示先转成 JPG），单张不超过 20MB，一个任务最多 12 张。
     */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO.Material upload(Long productId, MultipartFile file) {
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String nameExt = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (!NAME_EXTS.contains(nameExt)) {
            throw new BizException(TYPE_MESSAGE);
        }
        String ext = UploadRules.check(file, IMAGE_EXTS, VideoConstants.IMAGE_MAX_BYTES, TYPE_MESSAGE);
        VideoTaskDO t = taskForUpload(productId);
        requireEditable(t);
        List<VideoMaterialDO> existing = materials(t.getId());
        if (existing.size() >= VideoConstants.MAX_IMAGES) {
            throw new BizException("一个任务最多 " + VideoConstants.MAX_IMAGES + " 张图片");
        }
        int tenantId = t.getTenantId();
        FileStorageService.StoredFile s = files.storeForModel(StorageArea.MEDIA, tenantId, t.getBrand(), t.getModel(), file, ext);
        BizAttachmentDO a = new BizAttachmentDO();
        a.setTenantId(tenantId);
        a.setOwnerType(VideoConstants.OWNER_TASK);
        a.setOwnerId(t.getId());
        a.setKind(1);
        a.setStorage(s.storage());
        a.setFileKey(s.fileKey());
        a.setUrl("");
        a.setFileName(s.fileName());
        a.setFileSize(s.fileSize());
        a.setContentType(s.contentType());
        a.setProcessingStatus(VideoTranscoder.NONE);
        Long me = currentUser.resolve();
        a.setUploadedBy(me == null ? 0L : me);
        attachmentMapper.insert(a);
        VideoMaterialDO m = new VideoMaterialDO();
        m.setTenantId(tenantId);
        m.setTaskId(t.getId());
        m.setAttachmentId(a.getId());
        m.setSort(existing.stream().mapToInt(VideoMaterialDO::getSort).max().orElse(0) + 1);
        m.setUsed(1);
        materialMapper.insert(m);
        touch(t.getId());
        reindex(t);
        VideoTaskDetailVO.Material vo = new VideoTaskDetailVO.Material();
        vo.setAttachmentId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setFileSize(a.getFileSize());
        vo.setSort(m.getSort());
        vo.setUsed(true);
        return vo;
    }

    /** 顺序与选用：须包含任务的全部图片 */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO saveMaterials(Long id, MaterialsRequest req) {
        VideoTaskDO t = require(id);
        requireEditable(t);
        List<VideoMaterialDO> current = materials(id);
        Map<Long, VideoMaterialDO> byAttachment = current.stream()
                .collect(Collectors.toMap(VideoMaterialDO::getAttachmentId, Function.identity()));
        List<Long> given = req.getItems().stream().map(MaterialsRequest.Item::getAttachmentId).toList();
        if (given.size() != current.size() || !byAttachment.keySet().containsAll(given) || given.stream().distinct().count() != given.size()) {
            throw new BizException("图片已变化，请刷新后重试");
        }
        for (int i = 0; i < req.getItems().size(); i++) {
            MaterialsRequest.Item item = req.getItems().get(i);
            VideoMaterialDO m = byAttachment.get(item.getAttachmentId());
            VideoMaterialDO upd = new VideoMaterialDO();
            upd.setId(m.getId());
            upd.setSort(i + 1);
            upd.setUsed(item.isUsed() ? 1 : 0);
            materialMapper.updateById(upd);
        }
        touch(id);
        return detail(id);
    }

    /** 删除一张图片：同时移出素材库 */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO removeMaterial(Long id, Long attachmentId) {
        VideoTaskDO t = require(id);
        requireEditable(t);
        VideoMaterialDO m = materials(id).stream().filter(x -> Objects.equals(x.getAttachmentId(), attachmentId)).findFirst()
                .orElseThrow(() -> new BizException("图片不存在"));
        LocalDateTime now = LocalDateTime.now();
        VideoMaterialDO upd = new VideoMaterialDO();
        upd.setId(m.getId());
        upd.setDeletedAt(now);
        materialMapper.updateById(upd);
        BizAttachmentDO a = new BizAttachmentDO();
        a.setId(attachmentId);
        a.setDeletedAt(now);
        attachmentMapper.updateById(a);
        touch(id);
        reindex(t);
        return detail(id);
    }

    // ================================================================ 脚本

    /** 运营修改脚本：保存为手工脚本；是否符合加入队列的要求在脚本页提示，加入队列时再校验 */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO saveScript(Long id, VideoScript script) {
        VideoTaskDO t = require(id);
        requireEditable(t);
        if (t.getScriptStatus() == VideoConstants.SCRIPT_RUNNING) {
            throw new BizException("脚本正在生成，请稍后再改");
        }
        if (script == null || script.getShots() == null) {
            throw new BizException("请填写脚本");
        }
        if (script.getTitle() != null && script.getTitle().trim().length() > VideoConstants.MAX_TITLE) {
            throw new BizException("标题不能超过 " + VideoConstants.MAX_TITLE + " 个字符");
        }
        if (script.getShots().size() > VideoConstants.MAX_SHOTS) {
            throw new BizException("最多 " + VideoConstants.MAX_SHOTS + " 个镜头");
        }
        for (int i = 0; i < script.getShots().size(); i++) {
            String c = script.getShots().get(i).getCaption();
            if (c != null && c.trim().length() > VideoConstants.MAX_CAPTION) {
                throw new BizException("镜头 " + (i + 1) + " 的字幕超过 " + VideoConstants.MAX_CAPTION + " 个字符");
            }
        }
        script.setTitle(script.getTitle() == null ? "" : script.getTitle().trim());
        script.getShots().forEach(s -> s.setCaption(s.getCaption() == null ? "" : s.getCaption().trim()));
        VideoTaskDO upd = new VideoTaskDO();
        upd.setId(id);
        upd.setScript(writeScript(script));
        upd.setScriptSource(VideoConstants.SCRIPT_SOURCE_MANUAL);
        upd.setScriptStatus(VideoConstants.SCRIPT_READY);
        upd.setScriptError(ScriptRules.problem(script, usedAttachments(id)).orElse(""));
        taskMapper.updateById(upd);
        return detail(id);
    }

    // ================================================================ 队列

    /** 加入队列：待生成、已退回、生成失败、已通过（重新生成）可以加入；选用 4–8 张、脚本符合要求 */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO queue(Long id) {
        VideoTaskDO t = require(id);
        queueChecked(t);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchQueueVO batchQueue(List<Long> ids) {
        BatchQueueVO out = new BatchQueueVO();
        for (Long id : ids.stream().distinct().toList()) {
            VideoTaskDO t = taskMapper.selectById(id);
            if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), TenantContext.getTenantId())) {
                out.getSkipped().add(new BatchQueueVO.Skipped(id, "", "任务不存在"));
                continue;
            }
            try {
                queueChecked(t);
                out.setQueued(out.getQueued() + 1);
            } catch (BizException e) {
                out.getSkipped().add(new BatchQueueVO.Skipped(id, t.getModel(), e.getMessage()));
            }
        }
        return out;
    }

    private void queueChecked(VideoTaskDO t) {
        requireStatus(t, "加入队列", VideoConstants.STATUS_PENDING, VideoConstants.STATUS_REJECTED, VideoConstants.STATUS_FAILED,
                VideoConstants.STATUS_APPROVED);
        List<Long> used = usedAttachments(t.getId());
        if (used.size() < VideoConstants.MIN_SHOTS) {
            throw new BizException("选用 " + used.size() + " 张，至少要 " + VideoConstants.MIN_SHOTS + " 张");
        }
        if (used.size() > VideoConstants.MAX_SHOTS) {
            throw new BizException("选用 " + used.size() + " 张，最多 " + VideoConstants.MAX_SHOTS + " 张");
        }
        if (t.getScriptStatus() == VideoConstants.SCRIPT_RUNNING) {
            throw new BizException("脚本正在生成，请稍后再加入队列");
        }
        ScriptRules.problem(readScript(t), used).ifPresent(p -> {
            throw new BizException(p);
        });
        LocalDateTime now = LocalDateTime.now();
        int n = taskMapper.update(null, new LambdaUpdateWrapper<VideoTaskDO>()
                .set(VideoTaskDO::getStatus, VideoConstants.STATUS_QUEUED)
                .set(VideoTaskDO::getQueuedAt, now)
                .set(VideoTaskDO::getNextTryAt, null)
                .set(VideoTaskDO::getFailReason, "")
                .set(VideoTaskDO::getScriptError, "")
                .set(VideoTaskDO::getUpdateTime, now)
                .set(VideoTaskDO::getUpdateBy, operator())
                .eq(VideoTaskDO::getId, t.getId())
                .eq(VideoTaskDO::getStatus, t.getStatus()));
        if (n == 0) {
            throw new BizException("任务状态已变化，请刷新后重试");
        }
        addLog(t, VideoConstants.LOG_QUEUE, 0, "");
    }

    /** 移出队列：排队中 → 待生成 */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO dequeue(Long id) {
        VideoTaskDO t = require(id);
        requireStatus(t, "移出队列", VideoConstants.STATUS_QUEUED);
        int n = taskMapper.update(null, new LambdaUpdateWrapper<VideoTaskDO>()
                .set(VideoTaskDO::getStatus, VideoConstants.STATUS_PENDING)
                .set(VideoTaskDO::getQueuedAt, null)
                .set(VideoTaskDO::getNextTryAt, null)
                .set(VideoTaskDO::getUpdateTime, LocalDateTime.now())
                .set(VideoTaskDO::getUpdateBy, operator())
                .eq(VideoTaskDO::getId, id)
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_QUEUED));
        if (n == 0) {
            throw new BizException("任务已开始生成，不能移出队列");
        }
        addLog(t, VideoConstants.LOG_DEQUEUE, 0, "");
        return detail(id);
    }

    // ================================================================ 审核

    /** 通过：这次的两个视频进素材库，之前通过的视频标为已替换并移出素材库 */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO approve(Long id) {
        VideoTaskDO t = require(id);
        requireReviewable(t, "通过");
        List<VideoOutputDO> outputs = outputMapper.selectList(new LambdaQueryWrapper<VideoOutputDO>()
                .eq(VideoOutputDO::getTaskId, id).isNull(VideoOutputDO::getDeletedAt));
        for (VideoOutputDO o : outputs) {
            Integer status = Objects.equals(o.getVersion(), t.getCurrentVersion()) ? Integer.valueOf(VideoConstants.OUTPUT_APPROVED)
                    : Objects.equals(o.getStatus(), VideoConstants.OUTPUT_APPROVED) ? Integer.valueOf(VideoConstants.OUTPUT_REPLACED) : null;
            if (status != null && !status.equals(o.getStatus())) {
                VideoOutputDO upd = new VideoOutputDO();
                upd.setId(o.getId());
                upd.setStatus(status);
                outputMapper.updateById(upd);
            }
        }
        finishReview(t, VideoConstants.STATUS_APPROVED, "");
        addLog(t, VideoConstants.LOG_APPROVE, t.getCurrentVersion(), "");
        reindex(t);
        return detail(id);
    }

    /** 退回：必须填写原因（不超过 200 字）；素材库里已通过的旧视频不变 */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO reject(Long id, String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new BizException("请填写退回原因");
        }
        if (r.length() > VideoConstants.MAX_REJECT) {
            throw new BizException("退回原因不能超过 " + VideoConstants.MAX_REJECT + " 字");
        }
        VideoTaskDO t = require(id);
        requireReviewable(t, "退回");
        outputMapper.update(null, new LambdaUpdateWrapper<VideoOutputDO>()
                .set(VideoOutputDO::getStatus, VideoConstants.OUTPUT_REJECTED)
                .eq(VideoOutputDO::getTaskId, id)
                .eq(VideoOutputDO::getVersion, t.getCurrentVersion())
                .eq(VideoOutputDO::getStatus, VideoConstants.OUTPUT_REVIEW));
        finishReview(t, VideoConstants.STATUS_REJECTED, r);
        addLog(t, VideoConstants.LOG_REJECT, t.getCurrentVersion(), r);
        return detail(id);
    }

    private void requireReviewable(VideoTaskDO t, String action) {
        if (t.getStatus() == VideoConstants.STATUS_GENERATING) {
            throw new BizException("任务正在生成，生成完成后再审核");
        }
        requireStatus(t, action, VideoConstants.STATUS_REVIEW);
    }

    private void finishReview(VideoTaskDO t, int status, String reason) {
        int n = taskMapper.update(null, new LambdaUpdateWrapper<VideoTaskDO>()
                .set(VideoTaskDO::getStatus, status)
                .set(VideoTaskDO::getReviewBy, operator())
                .set(VideoTaskDO::getReviewTime, LocalDateTime.now())
                .set(VideoTaskDO::getRejectReason, reason)
                .set(VideoTaskDO::getUpdateTime, LocalDateTime.now())
                .set(VideoTaskDO::getUpdateBy, operator())
                .eq(VideoTaskDO::getId, t.getId())
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_REVIEW));
        if (n == 0) {
            throw new BizException("任务状态已变化，请刷新后重试");
        }
    }

    // ================================================================ 链接

    /** 任务里图片、视频、封面的临时链接：只能是这个任务的图片或它生成结果的文件 */
    public List<FileLink> links(Long id, List<Long> attachmentIds, boolean download) {
        VideoTaskDO t = require(id);
        Set<Long> outputIds = outputMapper.selectList(new LambdaQueryWrapper<VideoOutputDO>()
                        .select(VideoOutputDO::getId).eq(VideoOutputDO::getTaskId, id)).stream()
                .map(VideoOutputDO::getId).collect(Collectors.toSet());
        Map<Long, BizAttachmentDO> atts = attachments(attachmentIds);
        List<FileLink> out = new ArrayList<>(attachmentIds.size());
        for (Long aid : attachmentIds) {
            BizAttachmentDO a = atts.get(aid);
            boolean mine = a != null && a.getDeletedAt() == null && Objects.equals(a.getTenantId(), t.getTenantId())
                    && (VideoConstants.OWNER_TASK.equals(a.getOwnerType()) && Objects.equals(a.getOwnerId(), id)
                    || VideoConstants.OWNER_OUTPUT.equals(a.getOwnerType()) && outputIds.contains(a.getOwnerId()));
            if (!mine) {
                throw new BizException("文件不存在");
            }
            out.add(files.link(a.getStorage(), a.getFileKey(), a.getFileName(), download));
        }
        return out;
    }

    // ================================================================ 内部

    VideoTaskDO require(Long id) {
        VideoTaskDO t = id == null ? null : taskMapper.selectById(id);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("视频任务不存在");
        }
        return t;
    }

    private VideoTaskDO taskOfProduct(Long productId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<VideoTaskDO>()
                .eq(VideoTaskDO::getTenantId, TenantContext.getTenantId())
                .eq(VideoTaskDO::getProductId, productId)
                .isNull(VideoTaskDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 商品须是本公司可选用的；没有任务时创建（并发创建时以唯一键为准，取已有的） */
    private VideoTaskDO taskForUpload(Long productId) {
        VideoTaskDO t = taskOfProduct(productId);
        if (t != null) {
            return t;
        }
        ProductOptionVO p = productLookup.selectable(productId);
        ProductDO product = p == null ? null : productMapper.selectById(productId);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        t = new VideoTaskDO();
        t.setTenantId(TenantContext.getTenantId());
        t.setProductId(productId);
        // 型号与发品任务、素材库一致，用 mpn_raw 归并
        t.setBrand(p.getBrandName() == null ? "" : p.getBrandName().trim());
        t.setModel(product.getMpnRaw() == null ? "" : product.getMpnRaw().trim());
        t.setAssetKey(MediaIndexer.key(t.getBrand(), t.getModel()));
        t.setStatus(VideoConstants.STATUS_PENDING);
        t.setScriptSource(0);
        t.setScriptStatus(VideoConstants.SCRIPT_NONE);
        t.setScriptAiTaskId(0L);
        t.setScriptError("");
        t.setFailReason("");
        t.setReviewBy("");
        t.setRejectReason("");
        t.setCurrentVersion(0);
        try {
            taskMapper.insert(t);
            return t;
        } catch (DuplicateKeyException e) {
            log.info("视频任务已由并发请求创建，productId={}", productId);
            return taskOfProduct(productId);
        }
    }

    /** 排队中、生成中的任务不能修改素材和脚本 */
    void requireEditable(VideoTaskDO t) {
        if (t.getStatus() == VideoConstants.STATUS_QUEUED || t.getStatus() == VideoConstants.STATUS_GENERATING) {
            throw new BizException("任务" + VideoConstants.STATUS_NAMES.get(t.getStatus()) + "，不能修改素材和脚本"
                    + (t.getStatus() == VideoConstants.STATUS_QUEUED ? "，请先移出队列" : ""));
        }
    }

    static void requireStatus(VideoTaskDO t, String action, int... allowed) {
        for (int s : allowed) {
            if (t.getStatus() == s) {
                return;
            }
        }
        throw new BizException("任务当前为「" + VideoConstants.STATUS_NAMES.get(t.getStatus()) + "」，不能" + action);
    }

    List<VideoMaterialDO> materials(Long taskId) {
        return materialMapper.selectList(new LambdaQueryWrapper<VideoMaterialDO>()
                .eq(VideoMaterialDO::getTaskId, taskId)
                .isNull(VideoMaterialDO::getDeletedAt)
                .orderByAsc(VideoMaterialDO::getSort)
                .orderByAsc(VideoMaterialDO::getId));
    }

    /** 选用的图片附件，按顺序 */
    List<Long> usedAttachments(Long taskId) {
        return materials(taskId).stream().filter(m -> Objects.equals(m.getUsed(), 1)).map(VideoMaterialDO::getAttachmentId).toList();
    }

    private Map<Long, BizAttachmentDO> attachments(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        return keys.isEmpty() ? Map.of() : attachmentMapper.selectBatchIds(keys).stream()
                .collect(Collectors.toMap(BizAttachmentDO::getId, Function.identity()));
    }

    VideoScript readScript(VideoTaskDO t) {
        if (!StringUtils.hasText(t.getScript())) {
            return null;
        }
        try {
            return objectMapper.readValue(t.getScript(), VideoScript.class);
        } catch (JsonProcessingException e) {
            log.warn("视频任务 {} 的脚本无法解析：{}", t.getId(), e.getOriginalMessage());
            return null;
        }
    }

    String writeScript(VideoScript s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("脚本序列化失败", e);
        }
    }

    /** 资料待补（没有资料任务）或资料待核（还没核对或核对早于导入） */
    boolean factsUnverified(Long productId) {
        return productFacts.unverified(productId);
    }

    /** 按任务当前的图片与已通过视频重建素材库索引 */
    void reindex(VideoTaskDO t) {
        List<VideoOutputDO> outputs = outputMapper.selectList(new LambdaQueryWrapper<VideoOutputDO>()
                .eq(VideoOutputDO::getTaskId, t.getId()));
        mediaIndexer.indexVideoTask(t.getId(), t.getTenantId(), t.getBrand(), t.getModel(),
                outputs.stream().map(VideoOutputDO::getVideoAttachmentId).filter(x -> x != null && x > 0).toList(),
                outputs.stream().filter(o -> o.getDeletedAt() == null && Objects.equals(o.getStatus(), VideoConstants.OUTPUT_APPROVED))
                        .map(VideoOutputDO::getVideoAttachmentId).filter(x -> x != null && x > 0).toList());
    }

    private void touch(Long id) {
        taskMapper.update(null, new LambdaUpdateWrapper<VideoTaskDO>()
                .set(VideoTaskDO::getUpdateTime, LocalDateTime.now())
                .set(VideoTaskDO::getUpdateBy, operator())
                .eq(VideoTaskDO::getId, id));
    }

    void addLog(VideoTaskDO t, String action, int version, String note) {
        VideoLogDO l = new VideoLogDO();
        l.setTenantId(t.getTenantId());
        l.setTaskId(t.getId());
        l.setAction(action);
        l.setVersion(version);
        l.setNote(note == null ? "" : note.length() > 300 ? note.substring(0, 300) : note);
        String op = operator();
        l.setOperator("sys".equals(op) ? VideoConstants.SYSTEM : op);
        logMapper.insert(l);
    }

    /** 当前登录用户；后台任务、生成服务回调（匿名身份）没有登录用户时为 sys */
    static String operator() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken) {
            return "sys";
        }
        String name = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(name) ? name : "sys";
    }
}
