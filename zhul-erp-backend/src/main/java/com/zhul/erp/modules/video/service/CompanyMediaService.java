package com.zhul.erp.modules.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.media.dto.MediaDetailVO;
import com.zhul.erp.modules.media.dto.MediaItemVO;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import com.zhul.erp.modules.media.repository.MediaItemMapper;
import com.zhul.erp.modules.media.service.MediaLibraryService;
import com.zhul.erp.modules.media.support.MediaCategory;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductMediaDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMediaMapper;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.dto.CompanyMediaVO;
import com.zhul.erp.modules.video.entity.VideoOutputDO;
import com.zhul.erp.modules.video.repository.VideoOutputMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品档案「本公司素材」（spec product/product）：只读引用本公司素材库里这个型号的图片素材、视频素材、营销实拍；
 * 平台账号可以把其中一个「设为官方素材」：复制到共享商品库的公开存储，新建一条 product_media（来源「公司素材」）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyMediaService {

    private static final Set<Integer> CATEGORIES = Set.of(MediaCategory.MARKETING.code(), MediaCategory.IMAGE_ASSET.code(),
            MediaCategory.VIDEO_ASSET.code());
    public static final String OFFICIAL_SOURCE = "公司素材";

    private final ProductFinder productFinder;
    private final ProductBrandMapper brandMapper;
    private final MediaLibraryService mediaLibrary;
    private final MediaItemMapper itemMapper;
    private final BizAttachmentMapper attachmentMapper;
    private final VideoOutputMapper outputMapper;
    private final ProductMediaMapper productMediaMapper;
    private final FileStorageService files;
    private final PlatformScopeGuard platform;

    public CompanyMediaVO list(Long productId) {
        ProductDO p = productFinder.active(productId);
        String brand = brandName(p);
        CompanyMediaVO vo = new CompanyMediaVO();
        vo.setBrand(brand);
        vo.setModel(p.getMpnRaw());
        MediaDetailVO detail = mediaLibrary.detail(brand, p.getMpnRaw());
        List<MediaItemVO> items = detail.getGroups().stream().flatMap(g -> g.getItems().stream())
                .filter(i -> i.getItemId() != null && CATEGORIES.contains(i.getCategory())).toList();
        if (items.isEmpty()) {
            return vo;
        }
        List<MediaItemDO> rows = itemMapper.selectBatchIds(items.stream().map(MediaItemVO::getItemId).toList());
        java.util.Map<Long, Long> attachmentOf = rows.stream().collect(Collectors.toMap(MediaItemDO::getId, MediaItemDO::getSourceId));
        Set<Long> official = productMediaMapper.selectList(new LambdaQueryWrapper<ProductMediaDO>()
                        .select(ProductMediaDO::getSourceAttachmentId)
                        .eq(ProductMediaDO::getProductId, productId)
                        .in(ProductMediaDO::getSourceAttachmentId, attachmentOf.values())
                        .isNull(ProductMediaDO::getDeletedAt)).stream()
                .map(ProductMediaDO::getSourceAttachmentId).collect(Collectors.toSet());
        for (MediaItemVO i : items) {
            CompanyMediaVO.Item it = new CompanyMediaVO.Item();
            it.setMedia(i);
            it.setOfficial(official.contains(attachmentOf.get(i.getItemId())));
            vo.getItems().add(it);
        }
        return vo;
    }

    /** 设为官方素材：只有平台账号；同一个素材不能重复设置 */
    @Transactional(rollbackFor = Exception.class)
    public void makeOfficial(Long productId, Long itemId) {
        if (!platform.isPlatform()) {
            throw new BizException("只有平台账号能设为官方素材");
        }
        ProductDO p = productFinder.active(productId);
        MediaItemDO item = itemMapper.selectById(itemId);
        if (item == null || item.getDeletedAt() != null || !Objects.equals(item.getTenantId(), TenantContext.getTenantId())
                || !MediaCategory.BIZ_ATTACHMENT.equals(item.getSourceTable()) || !CATEGORIES.contains(item.getCategory())
                || !Objects.equals(item.getAssetKey(), com.zhul.erp.modules.media.service.MediaIndexer.key(brandName(p), p.getMpnRaw()))) {
            throw new BizException("素材不存在");
        }
        BizAttachmentDO a = attachmentMapper.selectById(item.getSourceId());
        if (a == null || a.getDeletedAt() != null) {
            throw new BizException("素材不存在");
        }
        boolean exists = productMediaMapper.selectCount(new LambdaQueryWrapper<ProductMediaDO>()
                .eq(ProductMediaDO::getProductId, productId)
                .eq(ProductMediaDO::getSourceAttachmentId, a.getId())
                .isNull(ProductMediaDO::getDeletedAt)) > 0;
        if (exists) {
            throw new BizException("已是官方素材");
        }
        boolean video = Objects.equals(a.getKind(), 2);
        String brand = brandName(p);
        FileStorageService.StoredFile copy = files.copyToModel(a.getStorage(), a.getFileKey(), StorageArea.PRODUCT,
                ProductConstants.PLATFORM_TENANT_ID, brand, p.getMpnRaw(), a.getFileName());
        String coverUrl = "";
        if (video) {
            VideoOutputDO o = outputMapper.selectOne(new LambdaQueryWrapper<VideoOutputDO>()
                    .eq(VideoOutputDO::getVideoAttachmentId, a.getId()).last("LIMIT 1"));
            BizAttachmentDO cover = o == null || o.getCoverAttachmentId() == null || o.getCoverAttachmentId() == 0 ? null
                    : attachmentMapper.selectById(o.getCoverAttachmentId());
            if (cover != null && cover.getDeletedAt() == null) {
                FileStorageService.StoredFile c = files.copyToModel(cover.getStorage(), cover.getFileKey(), StorageArea.PRODUCT,
                        ProductConstants.PLATFORM_TENANT_ID, brand, p.getMpnRaw(), cover.getFileName());
                coverUrl = files.publicUrl(c.storage(), c.fileKey());
            }
        }
        ProductMediaDO row = new ProductMediaDO();
        row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        row.setProductId(productId);
        row.setMediaType(video ? 2 : 1);
        row.setFileUrl(files.publicUrl(copy.storage(), copy.fileKey()));
        row.setStorageType(1);
        row.setStorage(copy.storage());
        row.setFileKey(copy.fileKey());
        row.setCoverUrl(coverUrl);
        String title = a.getFileName() == null ? "" : a.getFileName().replaceFirst("\\.[^.]+$", "");
        row.setTitle(title.length() > 128 ? title.substring(0, 128) : title);
        row.setFileSize(copy.fileSize());
        row.setProcessingStatus(0);
        row.setIsMain(0);
        row.setSource(OFFICIAL_SOURCE);
        row.setSourceAttachmentId(a.getId());
        row.setSortOrder(0);
        productMediaMapper.insert(row);
        log.info("本公司素材设为官方素材，productId={}，attachmentId={}", productId, a.getId());
    }

    private String brandName(ProductDO p) {
        ProductBrandDO b = p.getBrandId() == null ? null : brandMapper.selectById(p.getBrandId());
        return b == null ? "" : b.getBrandName();
    }
}
