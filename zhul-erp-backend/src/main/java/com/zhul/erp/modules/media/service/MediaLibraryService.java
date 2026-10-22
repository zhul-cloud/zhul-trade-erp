package com.zhul.erp.modules.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryAttachmentDO;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryAttachmentMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.media.dto.MediaDetailVO;
import com.zhul.erp.modules.media.dto.MediaFlagRequest;
import com.zhul.erp.modules.media.dto.MediaItemVO;
import com.zhul.erp.modules.media.dto.MediaModelQuery;
import com.zhul.erp.modules.media.dto.MediaModelRow;
import com.zhul.erp.modules.media.dto.MediaModelVO;
import com.zhul.erp.modules.media.dto.MediaRef;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import com.zhul.erp.modules.media.repository.MediaItemMapper;
import com.zhul.erp.modules.media.support.MediaCategory;
import com.zhul.erp.modules.media.support.MediaProducts;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductDocumentDO;
import com.zhul.erp.modules.product.entity.ProductMediaDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductDocumentMapper;
import com.zhul.erp.modules.product.repository.ProductMediaMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 素材库（规格 product/media-library）：按型号查本公司的素材（索引表）并合并共享商品库的官方图片与资料；
 * 修改「可发客户」「用于发品」标记；签发预览、下载链接（仅内部的不能下载）；网络图片的上传与删除。
 */
@Service
@RequiredArgsConstructor
public class MediaLibraryService {

    public static final String MENU = "/operations/media";
    public static final String PERM_UPLOAD = "media:library:upload";
    public static final String PERM_EDIT = "media:library:edit";
    private static final int MAX_NETWORK = 30;
    private static final long NETWORK_MAX_BYTES = 10L * 1024 * 1024;
    /** 官方资料里的产品文档类别 */
    private static final String OFFICIAL_MEDIA = MediaCategory.PRODUCT_MEDIA;
    private static final String OFFICIAL_DOC = MediaCategory.PRODUCT_DOCUMENT_TABLE;

    private final MediaItemMapper itemMapper;
    private final BizAttachmentMapper attachmentMapper;
    private final CustomerInquiryAttachmentMapper inquiryAttachmentMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final SupplierShipmentMapper shipmentMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final OutboundOrderMapper outboundMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final ProductMediaMapper productMediaMapper;
    private final ProductDocumentMapper productDocumentMapper;
    private final MediaProducts products;
    private final ProductCategoryMapper categoryMapper;
    private final FileStorageService files;
    private final AttachmentService attachments;
    private final InquiryLookups lookups;
    private final CurrentUserResolver currentUser;
    private final PermissionChecker perm;

    // ================================================================ 型号列表

    public PageResult<MediaModelVO> page(MediaModelQuery q) {
        int tenant = PiStore.tenantId();
        String kw = StringUtils.hasText(q.getKeyword()) ? "%" + q.getKeyword().trim().replace("%", "\\%").replace("_", "\\_") + "%" : null;
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = itemMapper.countModels(tenant, kw, q.getHasMarketing(), q.getHasListing());
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        List<MediaModelRow> rows = itemMapper.pageModels(tenant, kw, q.getHasMarketing(), q.getHasListing(), (long) (page - 1) * size, size);
        Map<String, Map<String, Integer>> groups = new HashMap<>();
        if (!rows.isEmpty()) {
            for (MediaItemDO i : itemMapper.selectList(new LambdaQueryWrapper<MediaItemDO>()
                    .select(MediaItemDO::getAssetKey, MediaItemDO::getCategory)
                    .eq(MediaItemDO::getTenantId, tenant)
                    .in(MediaItemDO::getAssetKey, rows.stream().map(MediaModelRow::getAssetKey).toList())
                    .isNull(MediaItemDO::getDeletedAt))) {
                MediaCategory.of(i.getCategory()).ifPresent(c ->
                        groups.computeIfAbsent(i.getAssetKey(), k -> new LinkedHashMap<>()).merge(c.group(), 1, Integer::sum));
            }
        }
        Map<String, ProductDO> matched = products.findAll(rows.stream().map(r -> new MediaIndexer.ModelRef(r.getBrand(), r.getModel())).toList());
        Map<Long, String> categories = categoryNames(matched.values());
        List<MediaModelVO> out = rows.stream().map(r -> {
            MediaModelVO vo = new MediaModelVO();
            vo.setBrand(r.getBrand());
            vo.setModel(r.getModel());
            ProductDO p = matched.get(r.getAssetKey());
            vo.setProductId(p == null ? null : p.getId());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setCategory(p == null ? null : categories.get(p.getCategoryId()));
            vo.setGroupCounts(groups.getOrDefault(r.getAssetKey(), Map.of()));
            vo.setHasMarketing(r.getMarketingCount() != null && r.getMarketingCount() > 0);
            vo.setSendableCount(r.getSendableCount());
            vo.setListingCount(r.getListingCount());
            vo.setLastAddedAt(r.getLastAddedAt());
            status(vo);
            return vo;
        }).toList();
        return PageResult.of(total, out);
    }

    /** 型号状态：已选发品素材 → 待选发品素材（有营销实拍）→ 缺营销实拍，取第一个满足的 */
    private static void status(MediaModelVO vo) {
        if (vo.getListingCount() != null && vo.getListingCount() > 0) {
            vo.setStatus("listed");
            vo.setStatusName("已选发品素材");
        } else if (Boolean.TRUE.equals(vo.getHasMarketing())) {
            vo.setStatus("marketing");
            vo.setStatusName("待选发品素材");
        } else {
            vo.setStatus("noMarketing");
            vo.setStatusName("缺营销实拍");
        }
    }

    private Map<Long, String> categoryNames(Collection<ProductDO> matched) {
        List<Long> ids = matched.stream().map(ProductDO::getCategoryId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        categoryMapper.selectBatchIds(ids).forEach(c -> out.put(c.getId(),
                StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName()));
        return out;
    }

    // ================================================================ 型号详情

    public MediaDetailVO detail(String brand, String model) {
        requireModel(brand, model);
        int tenant = PiStore.tenantId();
        String key = MediaIndexer.key(brand, model);
        List<MediaItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<MediaItemDO>()
                .eq(MediaItemDO::getTenantId, tenant)
                .eq(MediaItemDO::getAssetKey, key)
                .isNull(MediaItemDO::getDeletedAt)
                .orderByDesc(MediaItemDO::getId));
        List<MediaItemVO> items = new ArrayList<>(resolveOwn(rows.stream().filter(r -> !isOfficial(r.getSourceTable())).toList()));
        Optional<ProductDO> product = products.find(brand, model);
        Map<String, MediaItemDO> overrides = rows.stream().filter(r -> isOfficial(r.getSourceTable()))
                .collect(Collectors.toMap(r -> r.getSourceTable() + "|" + r.getSourceId(), r -> r, (a, b) -> a));
        product.ifPresent(p -> items.addAll(official(p, overrides)));

        MediaDetailVO vo = new MediaDetailVO();
        vo.setBrand(brand.trim());
        vo.setModel(model.trim());
        vo.setProductId(product.map(ProductDO::getId).orElse(null));
        vo.setProductName(product.map(ProductDO::getProductName).orElse(null));
        vo.setHasSendable(items.stream().anyMatch(i -> Boolean.TRUE.equals(i.getCanSend()) && i.getKind() != null && i.getKind() <= 2));
        Map<String, List<MediaItemVO>> grouped = new LinkedHashMap<>();
        for (MediaCategory c : MediaCategory.values()) {
            grouped.computeIfAbsent(c.group(), g -> new ArrayList<>());
        }
        items.stream().sorted(Comparator.comparing(MediaItemVO::getCategory))
                .forEach(i -> grouped.get(MediaCategory.of(i.getCategory()).orElseThrow().group()).add(i));
        vo.setGroups(grouped.entrySet().stream().map(e -> {
            MediaDetailVO.Group g = new MediaDetailVO.Group();
            g.setName(e.getKey());
            g.setItems(e.getValue());
            return g;
        }).toList());
        return vo;
    }

    /** 本公司素材：按来源表分批取文件信息与来源单据编号 */
    private List<MediaItemVO> resolveOwn(List<MediaItemDO> rows) {
        Map<Long, BizAttachmentDO> biz = byId(rows, MediaCategory.BIZ_ATTACHMENT, attachmentMapper::selectBatchIds, BizAttachmentDO::getId);
        Map<Long, CustomerInquiryAttachmentDO> inq = byId(rows, MediaCategory.INQUIRY_ATTACHMENT, inquiryAttachmentMapper::selectBatchIds,
                CustomerInquiryAttachmentDO::getId);
        Map<String, String> docNos = docNos(biz.values(), inq.values());
        List<Long> uploaders = biz.values().stream().map(BizAttachmentDO::getUploadedBy).toList();
        Map<Long, String> users = lookups.userNames(uploaders);
        List<MediaItemVO> out = new ArrayList<>(rows.size());
        for (MediaItemDO r : rows) {
            MediaItemVO vo = base(r);
            if (MediaCategory.BIZ_ATTACHMENT.equals(r.getSourceTable())) {
                BizAttachmentDO a = biz.get(r.getSourceId());
                if (a == null || a.getDeletedAt() != null) {
                    continue;
                }
                vo.setFileName(a.getFileName());
                vo.setFileSize(a.getFileSize());
                vo.setContentType(a.getContentType());
                vo.setProcessingStatus(a.getProcessingStatus());
                vo.setSourceType(a.getOwnerType());
                vo.setSourceId(a.getOwnerId());
                vo.setSourceNo(docNos.get(a.getOwnerType() + "|" + a.getOwnerId()));
                vo.setUploaderName(users.get(a.getUploadedBy()));
                vo.setUploadedAt(a.getCreateTime());
            } else {
                CustomerInquiryAttachmentDO a = inq.get(r.getSourceId());
                if (a == null || a.getDeletedAt() != null) {
                    continue;
                }
                vo.setFileName(a.getFileName());
                vo.setFileSize(a.getFileSize());
                vo.setContentType(a.getContentType());
                vo.setSourceType("INQUIRY");
                vo.setSourceId(a.getCustomerInquiryId());
                vo.setSourceNo(docNos.get("INQUIRY|" + a.getCustomerInquiryId()));
                vo.setUploaderName(a.getCreateBy());
                vo.setUploadedAt(a.getCreateTime());
            }
            out.add(vo);
        }
        return out;
    }

    private Map<String, String> docNos(Collection<BizAttachmentDO> biz, Collection<CustomerInquiryAttachmentDO> inq) {
        Map<String, List<Long>> owners = biz.stream().collect(Collectors.groupingBy(BizAttachmentDO::getOwnerType,
                Collectors.mapping(BizAttachmentDO::getOwnerId, Collectors.toList())));
        Map<String, String> out = new HashMap<>();
        load(owners.get(AttachmentService.SHIPMENT), shipmentMapper::selectBatchIds, SupplierShipmentDO::getId, SupplierShipmentDO::getSdNo)
                .forEach((id, no) -> out.put(AttachmentService.SHIPMENT + "|" + id, no));
        List<Long> receipts = new ArrayList<>();
        Optional.ofNullable(owners.get(AttachmentService.RECEIPT)).ifPresent(receipts::addAll);
        Optional.ofNullable(owners.get(AttachmentService.RECEIPT_DEFECT)).ifPresent(receipts::addAll);
        load(receipts, receiptMapper::selectBatchIds, PurchaseReceiptDO::getId, PurchaseReceiptDO::getGrNo).forEach((id, no) -> {
            out.put(AttachmentService.RECEIPT + "|" + id, no);
            out.put(AttachmentService.RECEIPT_DEFECT + "|" + id, no);
        });
        load(owners.get(AttachmentService.PACKING), outboundMapper::selectBatchIds, OutboundOrderDO::getId, OutboundOrderDO::getObNo)
                .forEach((id, no) -> out.put(AttachmentService.PACKING + "|" + id, no));
        Map<Long, Long> quoteInquiry = load(owners.get(AttachmentService.SOURCING_QUOTE), quoteMapper::selectBatchIds,
                SourcingQuoteDO::getId, SourcingQuoteDO::getCustomerInquiryId);
        List<Long> inquiryIds = new ArrayList<>(quoteInquiry.values());
        inq.forEach(a -> inquiryIds.add(a.getCustomerInquiryId()));
        Map<Long, String> codes = load(inquiryIds, inquiryMapper::selectBatchIds, CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryCode);
        codes.forEach((id, code) -> out.put("INQUIRY|" + id, code));
        quoteInquiry.forEach((quoteId, inquiryId) -> out.put(AttachmentService.SOURCING_QUOTE + "|" + quoteId, codes.get(inquiryId)));
        return out;
    }

    /** 共享商品库的图片、视频与资料；本公司改过标记的用覆盖行 */
    private List<MediaItemVO> official(ProductDO p, Map<String, MediaItemDO> overrides) {
        List<MediaItemVO> out = new ArrayList<>();
        for (ProductMediaDO m : productMediaMapper.selectList(new LambdaQueryWrapper<ProductMediaDO>()
                .eq(ProductMediaDO::getProductId, p.getId())
                .isNull(ProductMediaDO::getDeletedAt)
                .orderByDesc(ProductMediaDO::getIsMain).orderByAsc(ProductMediaDO::getSortOrder))) {
            MediaItemVO vo = officialBase(OFFICIAL_MEDIA, m.getId(), MediaCategory.OFFICIAL_IMAGE, overrides);
            vo.setKind(m.getMediaType());
            vo.setFileName(StringUtils.hasText(m.getTitle()) ? m.getTitle() : "官方图片");
            vo.setFileSize(m.getFileSize());
            vo.setProcessingStatus(m.getProcessingStatus());
            vo.setPublicUrl(m.getFileUrl());
            vo.setNote(m.getSource());
            vo.setSourceId(p.getId());
            out.add(vo);
        }
        for (ProductDocumentDO d : productDocumentMapper.selectList(new LambdaQueryWrapper<ProductDocumentDO>()
                .eq(ProductDocumentDO::getProductId, p.getId())
                .isNull(ProductDocumentDO::getDeletedAt)
                .orderByAsc(ProductDocumentDO::getSortOrder))) {
            MediaItemVO vo = officialBase(OFFICIAL_DOC, d.getId(), MediaCategory.PRODUCT_DOCUMENT, overrides);
            vo.setKind(3);
            vo.setFileName(d.getTitle());
            vo.setPublicUrl(d.getFileUrl());
            vo.setNote(d.getSource());
            vo.setSourceId(p.getId());
            out.add(vo);
        }
        return out;
    }

    private static MediaItemVO officialBase(String table, Long id, MediaCategory c, Map<String, MediaItemDO> overrides) {
        MediaItemDO o = overrides.get(table + "|" + id);
        MediaItemVO vo = o == null ? new MediaItemVO() : base(o);
        vo.setOfficialTable(table);
        vo.setOfficialId(id);
        vo.setCategory(c.code());
        vo.setCategoryName(c.label());
        vo.setListingAllowed(c.listingAllowed());
        vo.setSourceType("PRODUCT");
        if (o == null) {
            vo.setCanSend(c.canSendByDefault());
            vo.setForListing(false);
        }
        return vo;
    }

    private static MediaItemVO base(MediaItemDO r) {
        MediaItemVO vo = new MediaItemVO();
        vo.setItemId(r.getId());
        vo.setCategory(r.getCategory());
        MediaCategory c = MediaCategory.of(r.getCategory()).orElseThrow();
        vo.setCategoryName(c.label());
        vo.setListingAllowed(c.listingAllowed());
        vo.setKind(r.getKind());
        vo.setCanSend(Objects.equals(r.getCanSend(), 1));
        vo.setForListing(Objects.equals(r.getForListing(), 1));
        vo.setNote(r.getNote());
        return vo;
    }

    // ================================================================ 标记

    @Transactional(rollbackFor = Exception.class)
    public MediaItemVO flag(MediaFlagRequest req) {
        MediaItemDO item = locate(req.getRef(), true);
        MediaCategory c = MediaCategory.of(item.getCategory()).orElseThrow();
        boolean canSend = req.getCanSend() == null ? Objects.equals(item.getCanSend(), 1) : req.getCanSend();
        boolean forListing = req.getForListing() == null ? Objects.equals(item.getForListing(), 1) : req.getForListing();
        if (!canSend) {
            forListing = false;
        }
        if (forListing && !c.listingAllowed()) {
            throw new BizException("「" + c.label() + "」不能用于发品");
        }
        if (forListing && Objects.equals(item.getKind(), 3)) {
            throw new BizException("只有图片和视频能用于发品");
        }
        if (Boolean.TRUE.equals(req.getForListing()) && !canSend) {
            throw new BizException("先改为可发客户才能用于发品");
        }
        MediaItemDO upd = new MediaItemDO();
        upd.setId(item.getId());
        upd.setCanSend(canSend ? 1 : 0);
        upd.setForListing(forListing ? 1 : 0);
        Long me = currentUser.resolve();
        upd.setFlagBy(me == null ? 0L : me);
        upd.setFlagTime(LocalDateTime.now());
        itemMapper.updateById(upd);
        MediaItemDO saved = itemMapper.selectById(item.getId());
        return base(saved);
    }

    /**
     * 找到 ref 指的素材行；官方素材在本公司还没有覆盖行时，create 为 true 则补一行（默认标记），否则返回一条未入库的默认行。
     */
    private MediaItemDO locate(MediaRef ref, boolean create) {
        int tenant = PiStore.tenantId();
        if (ref.getItemId() != null) {
            MediaItemDO i = itemMapper.selectById(ref.getItemId());
            if (i == null || i.getDeletedAt() != null || !Objects.equals(i.getTenantId(), tenant)) {
                throw new BizException("素材不存在");
            }
            return i;
        }
        if (!isOfficial(ref.getOfficialTable()) || ref.getOfficialId() == null) {
            throw new BizException("素材不存在");
        }
        requireModel(ref.getBrand(), ref.getModel());
        ProductDO p = products.find(ref.getBrand(), ref.getModel()).orElseThrow(() -> new BizException("素材不存在"));
        boolean doc = OFFICIAL_DOC.equals(ref.getOfficialTable());
        Long productId = doc ? Optional.ofNullable(productDocumentMapper.selectById(ref.getOfficialId())).filter(d -> d.getDeletedAt() == null)
                .map(ProductDocumentDO::getProductId).orElse(null)
                : Optional.ofNullable(productMediaMapper.selectById(ref.getOfficialId())).filter(m -> m.getDeletedAt() == null)
                .map(ProductMediaDO::getProductId).orElse(null);
        if (!Objects.equals(productId, p.getId())) {
            throw new BizException("素材不存在");
        }
        String key = MediaIndexer.key(ref.getBrand(), ref.getModel());
        MediaItemDO existing = itemMapper.selectOne(new LambdaQueryWrapper<MediaItemDO>()
                .eq(MediaItemDO::getTenantId, tenant)
                .eq(MediaItemDO::getSourceTable, ref.getOfficialTable())
                .eq(MediaItemDO::getSourceId, ref.getOfficialId())
                .eq(MediaItemDO::getAssetKey, key)
                .isNull(MediaItemDO::getDeletedAt));
        if (existing != null) {
            return existing;
        }
        MediaCategory c = doc ? MediaCategory.PRODUCT_DOCUMENT : MediaCategory.OFFICIAL_IMAGE;
        MediaItemDO i = new MediaItemDO();
        i.setTenantId(tenant);
        i.setAssetKey(key);
        i.setBrand(ref.getBrand().trim());
        i.setModel(ref.getModel().trim());
        i.setCategory(c.code());
        i.setKind(doc ? 3 : Optional.ofNullable(productMediaMapper.selectById(ref.getOfficialId())).map(ProductMediaDO::getMediaType).orElse(1));
        i.setSourceTable(ref.getOfficialTable());
        i.setSourceId(ref.getOfficialId());
        i.setCanSend(c.canSendByDefault() ? 1 : 0);
        i.setForListing(0);
        i.setNote("");
        i.setFlagBy(0L);
        if (create) {
            itemMapper.insert(i);
        }
        return i;
    }

    // ================================================================ 链接

    /** 预览或下载链接：下载只允许可发客户的素材 */
    public List<FileLink> links(List<MediaRef> refs, boolean download) {
        List<FileLink> out = new ArrayList<>(refs.size());
        for (MediaRef ref : refs) {
            MediaItemDO item = locate(ref, false);
            if (download && !Objects.equals(item.getCanSend(), 1)) {
                throw new BizException("仅内部的素材不能下载");
            }
            out.add(link(item, download));
        }
        return out;
    }

    /**
     * 素材文件在哪里：本公司上传的和平台上传的官方素材有存储位置（storage + key）；外部链接的官方素材只有地址。
     * 只给可发客户的图片和视频（打包下载用）。
     */
    /** publicRead：文件在公开桶（商品官方素材），其余在私有桶 */
    public record FileSource(String storage, String key, String fileName, String externalUrl, boolean publicRead) {
    }

    public FileSource source(MediaRef ref) {
        MediaItemDO item = locate(ref, false);
        if (!Objects.equals(item.getCanSend(), 1)) {
            throw new BizException("仅内部的素材不能下载");
        }
        if (MediaCategory.BIZ_ATTACHMENT.equals(item.getSourceTable())) {
            BizAttachmentDO a = attachmentMapper.selectById(item.getSourceId());
            if (a == null || a.getDeletedAt() != null) {
                throw new BizException("素材不存在");
            }
            return new FileSource(a.getStorage(), a.getFileKey(), a.getFileName(), null, false);
        }
        if (MediaCategory.INQUIRY_ATTACHMENT.equals(item.getSourceTable())) {
            CustomerInquiryAttachmentDO a = inquiryAttachmentMapper.selectById(item.getSourceId());
            if (a == null || a.getDeletedAt() != null) {
                throw new BizException("素材不存在");
            }
            return new FileSource(a.getStorage(), a.getFileKey(), a.getFileName(), null, false);
        }
        ProductMediaDO m = OFFICIAL_DOC.equals(item.getSourceTable()) ? null : productMediaMapper.selectById(item.getSourceId());
        if (m == null || m.getDeletedAt() != null) {
            throw new BizException("素材不存在");
        }
        String url = m.getFileUrl() == null ? "" : m.getFileUrl();
        String name = url.substring(url.lastIndexOf('/') + 1);
        if (StringUtils.hasText(m.getStorage()) && StringUtils.hasText(m.getFileKey())) {
            return new FileSource(m.getStorage(), m.getFileKey(), StringUtils.hasText(name) ? name : item.getModel(), null, true);
        }
        return new FileSource(null, null, name, url, true);
    }

    private FileLink link(MediaItemDO item, boolean download) {
        if (MediaCategory.BIZ_ATTACHMENT.equals(item.getSourceTable())) {
            BizAttachmentDO a = attachmentMapper.selectById(item.getSourceId());
            if (a == null || a.getDeletedAt() != null) {
                throw new BizException("素材不存在");
            }
            return files.link(a.getStorage(), a.getFileKey(), a.getFileName(), download);
        }
        if (MediaCategory.INQUIRY_ATTACHMENT.equals(item.getSourceTable())) {
            CustomerInquiryAttachmentDO a = inquiryAttachmentMapper.selectById(item.getSourceId());
            if (a == null || a.getDeletedAt() != null) {
                throw new BizException("素材不存在");
            }
            return files.link(a.getStorage(), a.getFileKey(), a.getFileName(), download);
        }
        // 官方素材是公开文件或外部链接，固定地址
        String url = OFFICIAL_DOC.equals(item.getSourceTable())
                ? Optional.ofNullable(productDocumentMapper.selectById(item.getSourceId())).map(ProductDocumentDO::getFileUrl).orElse(null)
                : Optional.ofNullable(productMediaMapper.selectById(item.getSourceId())).map(ProductMediaDO::getFileUrl).orElse(null);
        if (!StringUtils.hasText(url)) {
            throw new BizException("素材不存在");
        }
        return new FileLink(url, item.getModel(), null);
    }

    // ================================================================ 网络图片

    @Transactional(rollbackFor = Exception.class)
    public MediaItemVO uploadNetwork(String brand, String model, String note, MultipartFile file) {
        requireModel(brand, model);
        int tenant = PiStore.tenantId();
        String key = MediaIndexer.key(brand, model);
        long count = itemMapper.selectCount(new LambdaQueryWrapper<MediaItemDO>()
                .eq(MediaItemDO::getTenantId, tenant)
                .eq(MediaItemDO::getAssetKey, key)
                .eq(MediaItemDO::getCategory, MediaCategory.NETWORK_IMAGE.code())
                .isNull(MediaItemDO::getDeletedAt));
        if (count >= MAX_NETWORK) {
            throw new BizException("每个型号最多 " + MAX_NETWORK + " 张网络图片");
        }
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > 500) {
            throw new BizException("来源说明不能超过 500 个字");
        }
        AttachmentVO a = attachments.uploadForModel(AttachmentService.NETWORK, brand.trim(), model.trim(), file);
        MediaItemDO i = new MediaItemDO();
        i.setTenantId(tenant);
        i.setAssetKey(key);
        i.setBrand(brand.trim());
        i.setModel(model.trim());
        i.setCategory(MediaCategory.NETWORK_IMAGE.code());
        i.setKind(1);
        i.setSourceTable(MediaCategory.BIZ_ATTACHMENT);
        i.setSourceId(a.getId());
        i.setCanSend(1);
        i.setForListing(0);
        i.setNote(cleanNote);
        i.setFlagBy(0L);
        itemMapper.insert(i);
        attachments.attach(AttachmentService.NETWORK, i.getId(), List.of(a.getId()));
        return resolveOwn(List.of(i)).get(0);
    }

    /** 删除网络图片：上传人本人，或有「修改素材标记」权限的人 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNetwork(Long itemId) {
        MediaItemDO i = locate(itemRef(itemId), false);
        if (!Objects.equals(i.getCategory(), MediaCategory.NETWORK_IMAGE.code())) {
            throw new BizException("只能删除网络图片");
        }
        BizAttachmentDO a = attachmentMapper.selectById(i.getSourceId());
        Long me = currentUser.resolve();
        if (!perm.has(PERM_EDIT) && (a == null || !Objects.equals(a.getUploadedBy(), me))) {
            throw new BizException("只能删除自己上传的网络图片");
        }
        if (a != null && a.getDeletedAt() == null) {
            attachments.remove(AttachmentService.NETWORK, i.getId(), a.getId());
        }
        MediaItemDO upd = new MediaItemDO();
        upd.setId(i.getId());
        upd.setDeletedAt(LocalDateTime.now());
        itemMapper.updateById(upd);
    }

    // ================================================================ 工具

    private static MediaRef itemRef(Long itemId) {
        MediaRef r = new MediaRef();
        r.setItemId(itemId);
        return r;
    }

    private static boolean isOfficial(String table) {
        return OFFICIAL_MEDIA.equals(table) || OFFICIAL_DOC.equals(table);
    }

    private static void requireModel(String brand, String model) {
        if (!StringUtils.hasText(model)) {
            throw new BizException("请指定型号");
        }
        if (brand == null) {
            throw new BizException("请指定品牌");
        }
    }

    private static <T> Map<Long, T> byId(List<MediaItemDO> rows, String table, Function<Collection<Long>, List<T>> loader,
                                         Function<T, Long> id) {
        List<Long> ids = rows.stream().filter(r -> table.equals(r.getSourceTable())).map(MediaItemDO::getSourceId).distinct().toList();
        return ids.isEmpty() ? Map.of() : loader.apply(ids).stream().collect(Collectors.toMap(id, t -> t, (a, b) -> a));
    }

    private static <T, V> Map<Long, V> load(List<Long> ids, Function<Collection<Long>, List<T>> loader, Function<T, Long> id,
                                            Function<T, V> value) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, V> out = new HashMap<>();
        loader.apply(ids.stream().distinct().toList()).forEach(t -> out.put(id.apply(t), value.apply(t)));
        return out;
    }
}
