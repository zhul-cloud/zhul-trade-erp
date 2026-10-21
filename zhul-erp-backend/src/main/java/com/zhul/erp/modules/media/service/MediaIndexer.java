package com.zhul.erp.modules.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentModelDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.attachment.repository.BizAttachmentModelMapper;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryAttachmentDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryAttachmentMapper;
import com.zhul.erp.modules.media.dto.AttachmentModelsRequest;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import com.zhul.erp.modules.media.repository.MediaItemMapper;
import com.zhul.erp.modules.media.support.MediaCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 素材索引的维护（design 决策 1、2）：各来源在挂上、删除、作废附件后调用，按来源当前的附件与型号重建索引——
 * 新增的补上、消失的软删除、已有的保留标记。素材文件仍归来源单据，这里不复制文件。
 */
@Service
@RequiredArgsConstructor
public class MediaIndexer {

    /** 需要型号标注的单据附件 → 素材类别 */
    private static final Map<String, MediaCategory> DOCUMENT_CATEGORIES = Map.of(
            AttachmentService.SHIPMENT, MediaCategory.SUPPLIER_SHIPMENT,
            AttachmentService.RECEIPT, MediaCategory.INSPECTION,
            AttachmentService.RECEIPT_DEFECT, MediaCategory.DEFECT,
            AttachmentService.PACKING, MediaCategory.PACKING);

    private final MediaItemMapper itemMapper;
    private final BizAttachmentMapper attachmentMapper;
    private final BizAttachmentModelMapper tagMapper;
    private final CustomerInquiryAttachmentMapper inquiryAttachmentMapper;

    /** 一个型号：品牌 + 型号 */
    public record ModelRef(String brand, String model) {
        public String assetKey() {
            return key(brand, model);
        }
    }

    /** 型号归并键：小写（去空格的品牌 + 空格 + 去空格的型号），与 ReceivingSupport.assetKey 一致 */
    public static String key(String brand, String model) {
        return ((brand == null ? "" : brand.trim()) + " " + (model == null ? "" : model.trim())).toLowerCase(Locale.ROOT);
    }

    // ================================================================ 单据附件（带型号标注）

    /**
     * 供方发货、验收入库、不良品、打包发货：保存单据后调用。
     *
     * @param docModels 单据里的全部型号
     * @param tags      前端提交的每个附件的型号；没提交的附件沿用已有标注，没有标注的默认为单据的全部型号
     */
    public void indexDocument(String ownerType, Long ownerId, int tenantId, List<ModelRef> docModels,
                              List<AttachmentModelsRequest> tags) {
        MediaCategory category = DOCUMENT_CATEGORIES.get(ownerType);
        if (category == null) {
            throw new IllegalArgumentException("这类附件不需要型号标注：" + ownerType);
        }
        Map<String, ModelRef> allowed = new LinkedHashMap<>();
        docModels.forEach(m -> allowed.putIfAbsent(m.assetKey(), new ModelRef(m.brand().trim(), m.model().trim())));
        Map<Long, List<ModelRef>> provided = new HashMap<>();
        if (tags != null) {
            for (AttachmentModelsRequest t : tags) {
                if (t.getAttachmentId() == null || t.getModels() == null) {
                    continue;
                }
                List<ModelRef> refs = t.getModels().stream().map(m -> new ModelRef(m.getBrand(), m.getModel())).toList();
                if (refs.stream().anyMatch(r -> !allowed.containsKey(r.assetKey()))) {
                    throw new BizException("只能标注本单据里的型号");
                }
                provided.put(t.getAttachmentId(), refs);
            }
        }
        List<BizAttachmentDO> all = ownerAttachments(ownerType, ownerId, tenantId);
        List<Desired> desired = new ArrayList<>();
        for (BizAttachmentDO a : all) {
            if (a.getDeletedAt() != null || a.getKind() == null || a.getKind() > 2) {
                continue;
            }
            Set<String> keys = tagKeys(a, provided.get(a.getId()), allowed);
            saveTags(a, keys, allowed);
            for (String k : keys) {
                ModelRef m = allowed.get(k);
                desired.add(new Desired(a.getId(), k, m.brand(), m.model(), category, a.getKind()));
            }
        }
        sync(tenantId, MediaCategory.BIZ_ATTACHMENT, all.stream().map(BizAttachmentDO::getId).toList(), desired);
    }

    /** 标注：提交了的用提交的；没提交的沿用已有（只保留仍在单据里的）；都没有时为单据的全部型号 */
    private Set<String> tagKeys(BizAttachmentDO a, List<ModelRef> provided, Map<String, ModelRef> allowed) {
        if (allowed.size() == 1) {
            return Set.copyOf(allowed.keySet());
        }
        if (provided != null) {
            return provided.stream().map(ModelRef::assetKey).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Set<String> existing = tagMapper.selectList(new LambdaQueryWrapper<BizAttachmentModelDO>()
                        .eq(BizAttachmentModelDO::getAttachmentId, a.getId())
                        .isNull(BizAttachmentModelDO::getDeletedAt)).stream()
                .map(BizAttachmentModelDO::getAssetKey).filter(allowed::containsKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return existing.isEmpty() ? Set.copyOf(allowed.keySet()) : existing;
    }

    private void saveTags(BizAttachmentDO a, Set<String> keys, Map<String, ModelRef> allowed) {
        Map<String, BizAttachmentModelDO> current = tagMapper.selectList(new LambdaQueryWrapper<BizAttachmentModelDO>()
                .eq(BizAttachmentModelDO::getAttachmentId, a.getId())).stream()
                .collect(Collectors.toMap(BizAttachmentModelDO::getAssetKey, t -> t, (x, y) -> x));
        LocalDateTime now = LocalDateTime.now();
        for (String k : keys) {
            BizAttachmentModelDO t = current.get(k);
            if (t == null) {
                t = new BizAttachmentModelDO();
                t.setTenantId(a.getTenantId());
                t.setAttachmentId(a.getId());
                t.setAssetKey(k);
                t.setBrand(allowed.get(k).brand());
                t.setModel(allowed.get(k).model());
                tagMapper.insert(t);
            } else if (t.getDeletedAt() != null) {
                t.setDeletedAt(null);
                tagMapper.updateById(t);
            }
        }
        current.values().stream().filter(t -> t.getDeletedAt() == null && !keys.contains(t.getAssetKey())).forEach(t -> {
            t.setDeletedAt(now);
            tagMapper.updateById(t);
        });
    }

    /** 单据作废、冲销：它的素材全部移出素材库 */
    public void retireDocument(String ownerType, Long ownerId, int tenantId) {
        List<Long> ids = ownerAttachments(ownerType, ownerId, tenantId).stream().map(BizAttachmentDO::getId).toList();
        sync(tenantId, MediaCategory.BIZ_ATTACHMENT, ids, List.of());
    }

    // ================================================================ 其他来源

    /** 实拍任务：图片归该型号的「营销实拍」 */
    public void indexShoot(Long taskId, int tenantId, String brand, String model) {
        List<BizAttachmentDO> all = ownerAttachments(AttachmentService.SHOOT, taskId, tenantId);
        List<Desired> desired = all.stream().filter(a -> a.getDeletedAt() == null && Objects.equals(a.getKind(), 1))
                .map(a -> new Desired(a.getId(), key(brand, model), brand.trim(), model.trim(), MediaCategory.MARKETING, 1))
                .toList();
        sync(tenantId, MediaCategory.BIZ_ATTACHMENT, all.stream().map(BizAttachmentDO::getId).toList(), desired);
    }

    /** 询价回价：图片归回价型号的「供方实拍」（PDF 报价单不算素材） */
    public void indexSourcingQuote(Long quoteId, int tenantId, String brand, String model) {
        List<BizAttachmentDO> all = ownerAttachments(AttachmentService.SOURCING_QUOTE, quoteId, tenantId);
        boolean hasModel = model != null && !model.isBlank();
        List<Desired> desired = all.stream().filter(a -> hasModel && a.getDeletedAt() == null && Objects.equals(a.getKind(), 1))
                .map(a -> new Desired(a.getId(), key(brand, model), brand == null ? "" : brand.trim(), model.trim(),
                        MediaCategory.SUPPLIER_PHOTO, 1))
                .toList();
        sync(tenantId, MediaCategory.BIZ_ATTACHMENT, all.stream().map(BizAttachmentDO::getId).toList(), desired);
    }

    /** 客户询盘：图片附件标注询盘单当前的全部型号，归「客户来图」（Excel、PDF 询价清单不算素材） */
    public void indexInquiry(Long inquiryId, int tenantId, Collection<ModelRef> models) {
        List<CustomerInquiryAttachmentDO> all = inquiryAttachmentMapper.selectList(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, inquiryId)
                .eq(CustomerInquiryAttachmentDO::getTenantId, tenantId));
        Map<String, ModelRef> distinct = new LinkedHashMap<>();
        models.stream().filter(m -> m.model() != null && !m.model().isBlank())
                .forEach(m -> distinct.putIfAbsent(m.assetKey(), new ModelRef(m.brand() == null ? "" : m.brand().trim(), m.model().trim())));
        List<Desired> desired = new ArrayList<>();
        for (CustomerInquiryAttachmentDO a : all) {
            if (a.getDeletedAt() != null || a.getContentType() == null || !a.getContentType().startsWith("image/")) {
                continue;
            }
            distinct.forEach((k, m) -> desired.add(new Desired(a.getId(), k, m.brand(), m.model(), MediaCategory.CUSTOMER_IMAGE, 1)));
        }
        sync(tenantId, MediaCategory.INQUIRY_ATTACHMENT, all.stream().map(CustomerInquiryAttachmentDO::getId).toList(), desired);
    }

    /** 这些型号里还没有营销实拍的（显示型号），保持传入顺序、去重 */
    public List<String> missingMarketing(int tenantId, Collection<ModelRef> models) {
        Map<String, ModelRef> distinct = new LinkedHashMap<>();
        models.forEach(m -> distinct.putIfAbsent(m.assetKey(), m));
        if (distinct.isEmpty()) {
            return List.of();
        }
        Set<String> has = itemMapper.selectList(new LambdaQueryWrapper<MediaItemDO>()
                        .select(MediaItemDO::getAssetKey)
                        .eq(MediaItemDO::getTenantId, tenantId)
                        .eq(MediaItemDO::getCategory, MediaCategory.MARKETING.code())
                        .in(MediaItemDO::getAssetKey, distinct.keySet())
                        .isNull(MediaItemDO::getDeletedAt)).stream()
                .map(MediaItemDO::getAssetKey).collect(Collectors.toSet());
        return distinct.entrySet().stream().filter(e -> !has.contains(e.getKey())).map(e -> e.getValue().model().trim()).toList();
    }

    // ================================================================ 同步

    private record Desired(Long sourceId, String assetKey, String brand, String model, MediaCategory category, int kind) {
    }

    /** 让 sourceIds 这些来源记录的索引行恰好等于 desired：新增、恢复、移出；已有行的标记不动 */
    private void sync(int tenantId, String sourceTable, Collection<Long> sourceIds, List<Desired> desired) {
        if (sourceIds.isEmpty()) {
            return;
        }
        Map<String, MediaItemDO> existing = itemMapper.selectList(new LambdaQueryWrapper<MediaItemDO>()
                        .eq(MediaItemDO::getTenantId, tenantId)
                        .eq(MediaItemDO::getSourceTable, sourceTable)
                        .in(MediaItemDO::getSourceId, sourceIds)).stream()
                .collect(Collectors.toMap(i -> i.getSourceId() + "|" + i.getAssetKey(), i -> i, (x, y) -> x));
        Set<String> wanted = new HashSet<>();
        for (Desired d : desired) {
            String k = d.sourceId() + "|" + d.assetKey();
            wanted.add(k);
            MediaItemDO cur = existing.get(k);
            if (cur == null) {
                MediaItemDO i = new MediaItemDO();
                i.setTenantId(tenantId);
                i.setAssetKey(d.assetKey());
                i.setBrand(d.brand());
                i.setModel(d.model());
                i.setCategory(d.category().code());
                i.setKind(d.kind());
                i.setSourceTable(sourceTable);
                i.setSourceId(d.sourceId());
                i.setCanSend(d.category().canSendByDefault() ? 1 : 0);
                i.setForListing(0);
                i.setNote("");
                i.setFlagBy(0L);
                itemMapper.insert(i);
            } else if (cur.getDeletedAt() != null || !Objects.equals(cur.getCategory(), d.category().code())
                    || !Objects.equals(cur.getKind(), d.kind())) {
                MediaItemDO upd = new MediaItemDO();
                upd.setId(cur.getId());
                upd.setCategory(d.category().code());
                upd.setKind(d.kind());
                itemMapper.updateById(upd);
                if (cur.getDeletedAt() != null) {
                    itemMapper.update(null, new LambdaUpdateWrapper<MediaItemDO>()
                            .set(MediaItemDO::getDeletedAt, null).eq(MediaItemDO::getId, cur.getId()));
                }
            }
        }
        LocalDateTime now = LocalDateTime.now();
        existing.forEach((k, cur) -> {
            if (!wanted.contains(k) && cur.getDeletedAt() == null) {
                MediaItemDO upd = new MediaItemDO();
                upd.setId(cur.getId());
                upd.setDeletedAt(now);
                itemMapper.updateById(upd);
            }
        });
    }

    private List<BizAttachmentDO> ownerAttachments(String ownerType, Long ownerId, int tenantId) {
        if (ownerId == null || ownerId <= 0) {
            return List.of();
        }
        return attachmentMapper.selectList(new LambdaQueryWrapper<BizAttachmentDO>()
                .eq(BizAttachmentDO::getTenantId, tenantId)
                .eq(BizAttachmentDO::getOwnerType, ownerType)
                .eq(BizAttachmentDO::getOwnerId, ownerId));
    }
}
